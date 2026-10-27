package com.komainos.catalogo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Nueva versión de una implementación: el código y sus parámetros quedan inmutables (RF26) */
public record VersionScriptPeticion(
        @NotBlank(message = "El código del script es obligatorio")
        @Size(max = 131072, message = "El código no puede superar los 128 KB")
        String contenido,

        @Size(max = 1000, message = "Las notas del cambio no pueden superar los 1000 caracteres")
        String notasCambio,

        @NotNull(message = "Indique los parámetros, aunque la lista esté vacía")
        @Size(max = 50, message = "Una versión admite hasta 50 parámetros")
        List<@NotNull @Valid ParametroPeticion> parametros) {
}
