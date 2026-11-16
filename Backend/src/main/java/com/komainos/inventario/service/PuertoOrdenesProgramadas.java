package com.komainos.inventario.service;

import com.komainos.inventario.model.CalendarioSemanal;

import java.time.Instant;
import java.util.List;

/** Implementado en planificación para que la dependencia vaya de planificación hacia inventario (DEC-27) */
public interface PuertoOrdenesProgramadas {

    /**
     * Órdenes del servidor que todavía no se ejecutan y cuyo horario programado en él no cabe en la ventana indicada
     * (RF18, DEC-60). Solo informa: cambiar la ventana no reprograma ninguna orden.
     */
    List<OrdenFueraDeVentana> fueraDeVentana(Integer idServidor, CalendarioSemanal ventana);

    /** @param estado el de la orden, como texto para que el inventario no dependa del mantenimiento */
    record OrdenFueraDeVentana(Integer idOrden, String codigo, String estado, Instant inicio, Instant fin) {
    }
}
