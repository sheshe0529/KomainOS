package com.komainos.mantenimiento.dominio;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado de ejecucion de un detalle de orden (R2.1, tabla 7;
 * {@code enum_estado_detalle_orden}). Es independiente del estado de la orden.
 */
public enum EstadoDetalleOrden {
    PENDIENTE,
    EN_COLA,
    EN_EJECUCION,
    FINALIZADO,
    FALLIDO,
    NO_INICIADO;

    private static final Map<EstadoDetalleOrden, Set<EstadoDetalleOrden>> TRANSICIONES = Map.of(
            PENDIENTE, EnumSet.of(EN_COLA, NO_INICIADO),
            EN_COLA, EnumSet.of(EN_EJECUCION, PENDIENTE, NO_INICIADO),
            EN_EJECUCION, EnumSet.of(FINALIZADO, FALLIDO),
            FINALIZADO, EnumSet.noneOf(EstadoDetalleOrden.class),
            FALLIDO, EnumSet.noneOf(EstadoDetalleOrden.class),
            NO_INICIADO, EnumSet.noneOf(EstadoDetalleOrden.class));

    public boolean puedePasarA(EstadoDetalleOrden destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    /** Un detalle no iniciado ya no ocupa su servidor en el cronograma. */
    public boolean ocupaServidor() {
        return this != NO_INICIADO;
    }
}
