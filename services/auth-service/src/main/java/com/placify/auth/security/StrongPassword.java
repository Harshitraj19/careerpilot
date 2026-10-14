package com.placify.auth.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password policy: 8-64 characters with at least one upper-case letter,
 * one lower-case letter, one digit and one special character.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPassword.Validator.class)
public @interface StrongPassword {

    String message() default "Password must be 8-64 characters with upper-case, lower-case, a number and a special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return isStrong(value);
        }

        public static boolean isStrong(String value) {
            if (value == null || value.length() < 8 || value.length() > 64) {
                return false;
            }
            boolean upper = false;
            boolean lower = false;
            boolean digit = false;
            boolean special = false;
            for (char c : value.toCharArray()) {
                if (Character.isUpperCase(c)) {
                    upper = true;
                } else if (Character.isLowerCase(c)) {
                    lower = true;
                } else if (Character.isDigit(c)) {
                    digit = true;
                } else if (!Character.isWhitespace(c)) {
                    special = true;
                }
            }
            return upper && lower && digit && special;
        }
    }
}
