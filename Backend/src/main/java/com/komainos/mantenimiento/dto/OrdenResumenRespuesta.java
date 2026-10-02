package com.komainos.mantenimiento.dto;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EtapaOrden;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.PrioridadOrden;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

/** Prioridad y etapa son derivadas, no se persisten */
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
        Instant fechaObjetivo,
        Instant inicioProgramado,
        Instant finProgramado,
        Instant fechaEvaluacion,
        Instant fechaCreacion) {
}
