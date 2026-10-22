package com.realtimechat.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Bio: at most 280 characters, optional, no control characters. */
@Documented
@Constraint(validatedBy = ValidBioValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBio {

    String message() default "Bio must be at most 280 characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
