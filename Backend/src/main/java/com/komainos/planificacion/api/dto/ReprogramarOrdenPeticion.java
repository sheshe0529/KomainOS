package com.komainos.planificacion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * RF30: nueva fecha con motivo obligatorio (HU18 CA3). Sin {@code inicio}, el
 * algoritmo busca el primer intervalo disponible desde la fecha objetivo.
 */
public record ReprogramarOrdenPeticion(
        OffsetDateTime inicio,
        @NotBlank(message = "El motivo de la reprogramación es obligatorio")
        @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
        String motivo) {
}
