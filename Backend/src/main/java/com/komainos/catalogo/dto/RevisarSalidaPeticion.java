package com.komainos.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Salida de un script de análisis ejecutado a mano, para comprobar el contrato antes de publicarlo */
public record RevisarSalidaPeticion(
        @NotBlank(message = "Pegue la salida del script")
        @Size(max = 2_000_000, message = "La salida no puede superar los 2 MB")
        String salida) {
}
