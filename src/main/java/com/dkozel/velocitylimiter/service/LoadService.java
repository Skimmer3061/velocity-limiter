package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.CustomerUsage;
import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.domain.UsageWindows;
import com.dkozel.velocitylimiter.domain.VelocityLimits;
import com.dkozel.velocitylimiter.domain.Violation;
import com.dkozel.velocitylimiter.repository.CustomerRepository;
import com.dkozel.velocitylimiter.repository.LoadAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoadService {

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
    public Optional<LoadDecision> process(LoadCommand command) {
        // one load per customer at a time; stays first, so the duplicate check, usage read and inserts all run under it
        customerRepository.lock(command.customerId());
        if (loadAttemptRepository.exists(command.customerId(), command.id())) {
            return Optional.empty();
        }
        CustomerUsage usage = loadAttemptRepository.usage(
                command.customerId(), UsageWindows.containing(command.loadedAt()));
        Set<Violation> violations = rules.stream()
                .map(rule -> rule.check(command.amount(), usage, limits))
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Violation.class)));
        boolean accepted = violations.isEmpty();
        loadAttemptRepository.insert(new LoadAttempt(
                command.customerId(), command.id(), command.amount(), command.loadedAt(), accepted));
        loadAttemptRepository.insertViolations(command.customerId(), command.id(), violations);
        return Optional.of(new LoadDecision(command.customerId(), command.id(), accepted));
    }
}
