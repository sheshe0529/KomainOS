package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

public record ConfiguracionRespuesta(
        Integer frecuenciaRevisionDias,
        Integer frecuenciaMantenimientoDias,
        ModalidadPlanificacion modalidadPlanificacion,
        ModoEjecucion modoEjecucion,
        /** Cuenta propia, nula si usa la predeterminada */
        Integer idCuentaServicio,
        boolean usaCuentaPredeterminada,
        /** La que se usará: la propia o la predeterminada, nula si no hay ninguna */
        ReferenciaSimple cuentaServicio,
        Instant fechaCreacion,
        Instant fechaActualizacion) {
}
