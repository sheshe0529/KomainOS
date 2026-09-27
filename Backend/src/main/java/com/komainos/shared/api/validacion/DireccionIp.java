package com.komainos.shared.api.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Direccion IPv4 o IPv6 escrita de forma literal (R2.4: servidor.direccion_ip). */
@Documented
@Constraint(validatedBy = ValidadorDireccionIp.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface DireccionIp {

    String message() default "La dirección IP no tiene un formato IPv4 o IPv6 válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
