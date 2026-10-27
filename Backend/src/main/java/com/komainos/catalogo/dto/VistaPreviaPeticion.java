package com.komainos.catalogo.dto;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Borrador del editor: se prepara igual que en la ejecución, sin guardarlo */
public record VistaPreviaPeticion(
        @NotNull(message = "Indique el sistema operativo: define el lenguaje del script")
        FamiliaSistemaOperativo familia,

        @Size(max = 131072, message = "El código no puede superar los 128 KB")
        String contenido,

        @Size(max = 50, message = "Una versión admite hasta 50 parámetros")
        List<@NotNull @Valid ParametroPeticion> parametros) {
}
