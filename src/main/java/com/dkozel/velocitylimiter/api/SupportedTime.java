package com.dkozel.velocitylimiter.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SupportedTimeValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedTime {

    String message() default "must be between 0001-01-01 and 9999-12-31 UTC";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
