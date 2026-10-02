package com.komainos.planificacion.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ProgramarOrdenPeticion(
        Integer idServidor,
        Integer idGrupo,
        OffsetDateTime inicio,
        @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
        String motivo) {
}
