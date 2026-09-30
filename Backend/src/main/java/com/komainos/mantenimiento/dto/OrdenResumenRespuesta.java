package com.komainos.mantenimiento.dto;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EtapaOrden;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.PrioridadOrden;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

/**
 * Orden en la consulta y en el cronograma (RF32, RF36). Prioridad y etapa son
 * atributos derivados que no se persisten.
 *
 * @param tipoObjetivo INDIVIDUAL o GRUPAL
 * @param objetivo     servidor (hostname) o grupo (nombre) de la orden
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
        Instant fechaObjetivo,
        Instant inicioProgramado,
        Instant finProgramado,
        Instant fechaEvaluacion,
        Instant fechaCreacion) {
}
