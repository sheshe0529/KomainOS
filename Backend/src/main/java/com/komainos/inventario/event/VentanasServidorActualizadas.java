package com.komainos.inventario.event;

import com.komainos.shared.model.Actor;

/** La planificación lo escucha para reprogramar las órdenes afectadas (DEC-18), así el inventario no depende de ella */
public record VentanasServidorActualizadas(Integer idServidor, Actor actor) {

    public static VentanasServidorActualizadas deServidor(Integer idServidor, Actor actor) {
        return new VentanasServidorActualizadas(idServidor, actor);
    }
}
