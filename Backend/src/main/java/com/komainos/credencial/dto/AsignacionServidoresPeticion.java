package com.komainos.credencial.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AsignacionServidoresPeticion(
        @NotEmpty(message = "Seleccione al menos un servidor")
        @Size(max = 500, message = "Se pueden asignar hasta 500 servidores por operación")
        List<@NotNull Integer> idsServidores) {
}
