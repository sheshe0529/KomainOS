package com.komainos.inventario.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BajaPeticion(
        @NotBlank(message = "El motivo de la baja es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {
}
