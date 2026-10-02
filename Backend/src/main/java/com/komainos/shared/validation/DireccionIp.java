package com.komainos.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Admite TYPE_USE para validar cada elemento de una lista de direcciones */
@Documented
@Constraint(validatedBy = ValidadorDireccionIp.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DireccionIp {

    String message() default "La dirección IP no tiene un formato IPv4 o IPv6 válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
