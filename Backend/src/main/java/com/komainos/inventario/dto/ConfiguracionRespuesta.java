package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

public record ConfiguracionRespuesta(
        /** Nula con la revisión periódica inactiva y sin frecuencia definida (DEC-60) */
        Integer frecuenciaRevisionDias,
        boolean revisionPeriodicaHabilitada,
        Integer frecuenciaMantenimientoDias,
        ModalidadPlanificacion modalidadPlanificacion,
        ModoEjecucion modoEjecucion,
        /** Cuenta asignada para cada finalidad, nula si no tiene: no hay cuenta predeterminada (DEC-60) */
        Integer idCuentaServicioEvaluacion,
        Integer idCuentaServicioMantenimiento,
        ReferenciaSimple cuentaServicioEvaluacion,
        ReferenciaSimple cuentaServicioMantenimiento,
        Instant fechaCreacion,
        Instant fechaActualizacion) {
}
