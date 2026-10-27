package com.komainos.mantenimiento.event;

import com.komainos.shared.model.Actor;

/** Se publica al cerrar una orden: la planificación genera el siguiente ciclo automático después del commit (RF29) */
public record CicloMantenimientoCerrado(Integer idOrden, Actor actor) {
}
