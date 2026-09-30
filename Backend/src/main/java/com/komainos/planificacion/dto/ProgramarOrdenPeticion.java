package com.komainos.planificacion.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * RF30: programar una orden para un servidor o un grupo (uno de los dos).
 * Si se indica {@code inicio}, se verifica ventana, capacidad y conflictos;
 * si no, el algoritmo toma el primer intervalo disponible.
 */
public record ProgramarOrdenPeticion(
        Integer idServidor,
        Integer idGrupo,
        OffsetDateTime inicio,
        @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
        String motivo) {
}
