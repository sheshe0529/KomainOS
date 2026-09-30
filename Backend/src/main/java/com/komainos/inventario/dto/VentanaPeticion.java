package com.komainos.inventario.dto;

import com.komainos.inventario.model.DiaSemana;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Intervalo de la ventana permisiva (RF18, RF19). Para un intervalo que cruza
 * la medianoche se indica el dia siguiente como {@code diaFin}; para terminar
 * a medianoche, {@code horaFin} 00:00 del dia siguiente.
 */
public record VentanaPeticion(
        @NotNull(message = "El día de inicio es obligatorio")
        DiaSemana diaInicio,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime horaInicio,

        @NotNull(message = "El día de fin es obligatorio")
        DiaSemana diaFin,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime horaFin) {
}
