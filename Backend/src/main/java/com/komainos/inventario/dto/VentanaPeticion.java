package com.komainos.inventario.dto;

import com.komainos.inventario.model.DiaSemana;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Un intervalo que cruza la medianoche termina en el día siguiente (diaFin) */
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
