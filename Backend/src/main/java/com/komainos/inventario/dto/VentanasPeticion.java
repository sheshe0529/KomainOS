package com.komainos.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VentanasPeticion(
        @NotNull(message = "La lista de ventanas es obligatoria")
        @Size(max = 50, message = "No se admiten más de 50 intervalos")
        List<@Valid @NotNull(message = "Un intervalo de la ventana está vacío") VentanaPeticion> ventanas) {
}
