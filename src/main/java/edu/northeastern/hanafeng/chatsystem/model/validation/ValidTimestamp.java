package edu.northeastern.hanafeng.chatsystem.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimestampValidator.class)
@Documented
public @interface ValidTimestamp {
    String message() default "timestamp must be a valid ISO-8601 instant between 2020 and current time";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
