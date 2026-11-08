package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

public record ConfiguracionRespuesta(
        Integer frecuenciaRevisionDias,
        boolean revisionPeriodicaHabilitada,
        Integer frecuenciaMantenimientoDias,
        ModalidadPlanificacion modalidadPlanificacion,
        ModoEjecucion modoEjecucion,
        /** Cuenta propia, nula si usa la predeterminada */
        Integer idCuentaServicioEvaluacion,
        Integer idCuentaServicioMantenimiento,
        boolean usaPredeterminadaEvaluacion,
        boolean usaPredeterminadaMantenimiento,
        /** La que se usará: la propia o la predeterminada, nula si no hay ninguna */
        ReferenciaSimple cuentaServicioEvaluacion,
        ReferenciaSimple cuentaServicioMantenimiento,
        Instant fechaCreacion,
        Instant fechaActualizacion) {
}
