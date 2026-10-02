package com.komainos.planificacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ReprogramarOrdenPeticion(
        OffsetDateTime inicio,
        @NotBlank(message = "El motivo de la reprogramación es obligatorio")
        @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
        String motivo) {
}
