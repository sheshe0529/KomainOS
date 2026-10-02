package com.komainos.inventario.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record IntegrantesPeticion(
        @NotNull(message = "La lista de servidores es obligatoria")
        List<@NotNull(message = "Un identificador de servidor está vacío") Integer> idsServidores) {
}
