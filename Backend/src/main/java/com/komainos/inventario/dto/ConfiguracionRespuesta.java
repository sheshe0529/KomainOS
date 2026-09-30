package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;

import java.time.Instant;

/**
 * Configuracion de mantenimiento (RF17). {@code usaCuentaPredeterminada} es
 * verdadero cuando la configuracion no define cuenta propia (R2.4).
 * {@code modoEjecucion} solo aplica a grupos.
 */
public record ConfiguracionRespuesta(
        Integer frecuenciaRevisionDias,
        Integer frecuenciaMantenimientoDias,
        ModalidadPlanificacion modalidadPlanificacion,
        ModoEjecucion modoEjecucion,
        Integer idCuentaServicio,
        boolean usaCuentaPredeterminada,
        Instant fechaCreacion,
        Instant fechaActualizacion) {
}
