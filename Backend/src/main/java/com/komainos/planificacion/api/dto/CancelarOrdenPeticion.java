package com.komainos.planificacion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarOrdenPeticion(
        @NotBlank(message = "El motivo de la cancelación es obligatorio")
        @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
        String motivo) {
}
