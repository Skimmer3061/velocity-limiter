package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.LoadOutcome;
import com.dkozel.velocitylimiter.domain.Period;
import com.dkozel.velocitylimiter.domain.Usage;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import com.dkozel.velocitylimiter.repository.CustomerRepository;
import com.dkozel.velocitylimiter.repository.LoadAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoadService {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);

    private final CustomerRepository customerRepository;
    private final LoadAttemptRepository loadAttemptRepository;
    private final List<VelocityRule> rules;
    private final VelocityLimits limits;

    LoadService(CustomerRepository customerRepository, LoadAttemptRepository loadAttemptRepository,
                List<VelocityRule> rules, VelocityLimitsProperties limitsProperties) {
        this.customerRepository = customerRepository;
        this.loadAttemptRepository = loadAttemptRepository;
        this.rules = rules;
        this.limits = limitsProperties.toLimits();
    }

    @Transactional
    public LoadOutcome process(LoadCommand command) {
        // one load per customer at a time; stays first, so the duplicate check, usage read and inserts all run under it
        customerRepository.lock(command.customerId());
        Optional<LoadAttempt> earlier = loadAttemptRepository.find(command.customerId(), command.id());
        if (earlier.isPresent()) {
            LoadAttempt original = earlier.get();
            LoadDecision decision = new LoadDecision(original.customerId(), original.id(), original.accepted());
            boolean samePayload = original.amount().compareTo(command.amount()) == 0
                    && original.loadedAt().equals(command.loadedAt());
            log.debug("Repeated load: customerId={}, id={}, samePayload={}", command.customerId(), command.id(), samePayload);
            return samePayload ? new LoadOutcome.Replayed(decision) : new LoadOutcome.Conflicting(decision);
        }
        // one usage query per period the rules use (day and week today), each over that period's window
        Map<Period, Usage> usage = new EnumMap<>(Period.class);
        for (VelocityRule rule : rules) {
            usage.computeIfAbsent(rule.period(), period -> loadAttemptRepository.usage(
                    command.customerId(), period.windowContaining(command.loadedAt())));
        }
        Set<Violation> violations = rules.stream()
                .map(rule -> rule.check(command.amount(), usage.get(rule.period()), limits))
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Violation.class)));
        boolean accepted = violations.isEmpty();
        loadAttemptRepository.insert(new LoadAttempt(
                command.customerId(), command.id(), command.amount(), command.loadedAt(), accepted));
        loadAttemptRepository.insertViolations(command.customerId(), command.id(), violations);
        log.debug("Load decided: customerId={}, id={}, accepted={}, violations={}",
                command.customerId(), command.id(), accepted, violations);
        return new LoadOutcome.Decided(new LoadDecision(command.customerId(), command.id(), accepted));
    }
}
