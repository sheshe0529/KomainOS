package com.komainos.catalogo.dto;

import com.komainos.ejecucion.service.PreparadorScript;
import com.komainos.inventario.model.TipoDato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Parámetro de una versión: en la ejecución es una variable con este nombre y su valor por defecto (RF25) */
public record ParametroPeticion(
        @NotBlank(message = "El nombre del parámetro es obligatorio")
        @Pattern(regexp = PreparadorScript.PATRON_NOMBRE,
                message = "El nombre del parámetro solo admite letras sin tilde, números y guion bajo, sin empezar por un número")
        String nombre,

        @NotNull(message = "Indique el tipo del parámetro")
        TipoDato tipo,

        boolean obligatorio,

        @Size(max = 1000, message = "El valor por defecto no puede superar los 1000 caracteres")
        String valorPorDefecto) {
}
