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

    private final LoadAttemptRepository repository;

    LoadService(LoadAttemptRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<LoadDecision> process(LoadCommand command) {
        boolean accepted = true; // TODO: replace with the velocity rules
        repository.insert(new LoadAttempt(
                command.customerId(), command.id(), command.amount(), command.loadedAt(), accepted));
        return Optional.of(new LoadDecision(command.customerId(), command.id(), accepted));
    }
}
