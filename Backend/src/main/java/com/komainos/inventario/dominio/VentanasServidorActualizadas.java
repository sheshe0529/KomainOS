package com.komainos.inventario.dominio;

import com.komainos.shared.dominio.Actor;

/**
 * Evento de dominio: cambio la ventana permisiva de un servidor (RF18, RF19).
 *
 * <p>Cambiar los integrantes de un grupo no lo publica: las ordenes grupales
 * ya generadas conservan sus integrantes (RF20), asi que su ventana efectiva
 * no cambia por eso.
 *
 * <p>La planificacion lo escucha para aplicar el cambio a los proximos
 * mantenimientos (HU14 CA3, DEC-18). Se publica como evento para que el
 * inventario no dependa de la planificacion.
 */
public record VentanasServidorActualizadas(Integer idServidor, Actor actor) {

    public static VentanasServidorActualizadas deServidor(Integer idServidor, Actor actor) {
        return new VentanasServidorActualizadas(idServidor, actor);
    }
}
