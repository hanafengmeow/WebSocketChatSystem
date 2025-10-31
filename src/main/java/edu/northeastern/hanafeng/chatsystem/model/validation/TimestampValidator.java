package edu.northeastern.hanafeng.chatsystem.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;

public class TimestampValidator implements ConstraintValidator<ValidTimestamp, Instant> {
    private static final Instant MIN_TIME = Instant.parse("2020-01-01T00:00:00Z");

    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        Instant maxTime = Instant.now().plusSeconds(86400);
        return !value.isBefore(MIN_TIME) && !value.isAfter(maxTime);
    }
}
