package com.dkozel.velocitylimiter.service;

import com.dkozel.velocitylimiter.domain.LoadAttempt;
import com.dkozel.velocitylimiter.domain.LoadCommand;
import com.dkozel.velocitylimiter.domain.LoadDecision;
import com.dkozel.velocitylimiter.repository.LoadAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LoadService {

    private final LoadAttemptRepository loadAttemptRepository;

    LoadService(LoadAttemptRepository repository) {
        this.loadAttemptRepository = repository;
    }

    @Transactional
    public Optional<LoadDecision> process(LoadCommand command) {
        if (loadAttemptRepository.exists(command.customerId(), command.id())) {
            return Optional.empty();
        }
        boolean accepted = true; // TODO: replace with the velocity rules
        loadAttemptRepository.insert(new LoadAttempt(
                command.customerId(), command.id(), command.amount(), command.loadedAt(), accepted));
        return Optional.of(new LoadDecision(command.customerId(), command.id(), accepted));
    }
}
