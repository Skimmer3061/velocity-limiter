package com.dkozel.velocitylimiter.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;

public class SupportedTimeValidator implements ConstraintValidator<SupportedTime, Instant> {

    private static final Instant FIRST = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("+10000-01-01T00:00:00Z");

    @Override
    public boolean isValid(Instant time, ConstraintValidatorContext context) {
        return time == null || (!time.isBefore(FIRST) && time.isBefore(END));
    }
}
