package com.komainos.inventario.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrupoPeticion(
        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion) {
}
