package com.realtimechat.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Display name: 1–64 characters after control-character stripping and trimming. */
@Documented
@Constraint(validatedBy = ValidDisplayNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDisplayName {

    String message() default "Display name must be between 1 and 64 characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
