package com.komainos.mantenimiento.dto;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EtapaOrden;
import com.komainos.mantenimiento.model.MotivoCierre;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.PrioridadOrden;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

/**
 * Prioridad y etapa son derivadas, no se persisten. El motivo, la fecha y las observaciones de cierre solo están en una
 * orden cerrada (DEC-61)
 */
public record OrdenResumenRespuesta(
        Integer id,
        String codigo,
        String tipoObjetivo,
        ReferenciaSimple objetivo,
        int cantidadServidores,
        CriticidadResumen criticidad,
        OrigenOrden origen,
        PrioridadOrden prioridad,
        EstadoOrden estado,
        EtapaOrden etapa,
        MotivoCierre motivoCierre,
        Instant fechaCierre,
        String observacionesCierre,
        Instant fechaObjetivo,
        Instant inicioProgramado,
        Instant finProgramado,
        Instant fechaEvaluacion,
        Instant fechaCreacion) {
}
