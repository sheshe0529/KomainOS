package com.komainos.inventario.dto;

import com.komainos.inventario.service.PuertoOrdenesProgramadas.OrdenFueraDeVentana;

import java.time.Instant;

/** Orden pendiente cuyo horario programado en el servidor queda fuera de la ventana propuesta (DEC-60) */
public record OrdenFueraVentanaRespuesta(Integer idOrden, String codigo, String estado, Instant inicio, Instant fin) {

    public static OrdenFueraVentanaRespuesta de(OrdenFueraDeVentana o) {
        return new OrdenFueraVentanaRespuesta(o.idOrden(), o.codigo(), o.estado(), o.inicio(), o.fin());
    }
}
