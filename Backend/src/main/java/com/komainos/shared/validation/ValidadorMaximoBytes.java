package com.komainos.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ValidadorMaximoBytes implements ConstraintValidator<MaximoBytes, String> {

    private int maximo;

    @Override
    public void initialize(MaximoBytes anotacion) {
        this.maximo = anotacion.value();
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || bytes(valor) <= maximo;
    }

    public static int bytes(String valor) {
        return valor.getBytes(StandardCharsets.UTF_8).length;
    }
}
