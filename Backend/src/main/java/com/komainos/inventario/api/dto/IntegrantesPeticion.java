package com.komainos.inventario.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Conjunto completo de integrantes del grupo (RF20). */
public record IntegrantesPeticion(
        @NotNull(message = "La lista de servidores es obligatoria")
        List<@NotNull(message = "Un identificador de servidor está vacío") Integer> idsServidores) {
}
