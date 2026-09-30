package com.komainos.inventario.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.komainos.inventario.model.DiaSemana;

import java.time.LocalTime;

public record VentanaRespuesta(
        DiaSemana diaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        DiaSemana diaFin,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
        long duracionMinutos) {
}
