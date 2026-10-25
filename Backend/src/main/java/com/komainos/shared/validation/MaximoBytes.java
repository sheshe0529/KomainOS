package com.komainos.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Largo en bytes UTF-8, no en caracteres: una ñ o una vocal con tilde ocupan dos */
@Documented
@Constraint(validatedBy = ValidadorMaximoBytes.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaximoBytes {

    int value();

    String message() default "El valor supera el largo permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
