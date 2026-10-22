package com.realtimechat.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Message body: 1–4000 characters, no control characters other than newline and tab. */
@Documented
@Constraint(validatedBy = ValidMessageContentValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMessageContent {

    String message() default "Message must be between 1 and 4000 characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
