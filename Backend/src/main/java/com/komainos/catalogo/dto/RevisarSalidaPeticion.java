package com.komainos.catalogo.dto;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Salida de un script de análisis ejecutado a mano, para comprobar el contrato antes de publicarlo */
public record RevisarSalidaPeticion(
        @NotBlank(message = "Pegue la salida del script")
        @Size(max = 2_000_000, message = "La salida no puede superar los 2 MB")
        String salida,

        /** Sistema operativo del script: solo se exigen las métricas que se miden en él; sin valor, todas (R3.1) */
        FamiliaSistemaOperativo familia) {
}
