package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.ModalidadPlanificacion;
import com.komainos.inventario.dominio.ModoEjecucion;

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
