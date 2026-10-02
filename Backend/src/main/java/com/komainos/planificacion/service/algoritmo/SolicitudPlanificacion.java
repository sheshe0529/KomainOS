package com.komainos.planificacion.service.algoritmo;

import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.ModoEjecucion;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Entradas del algoritmo: ventana efectiva, duración por servidor (DEC-08), capacidad (RF68), zona (DEC-06) y horizonte (DEC-12) */
public record SolicitudPlanificacion(
        List<Integer> idsServidores,
        ModoEjecucion modoEjecucion,
        Instant fechaObjetivo,
        Duration plazoAutorizacion,
        CalendarioSemanal ventana,
        Duration duracionPorServidor,
        int capacidad,
        Instant ahora,
        ZoneId zona,
        Duration horizonte) {

    public SolicitudPlanificacion {
        if (idsServidores == null || idsServidores.isEmpty()) {
            throw new IllegalArgumentException("La planificacion necesita al menos un servidor");
        }
        if (capacidad < 1) {
            throw new IllegalArgumentException("La capacidad debe ser de al menos 1 servidor");
        }
        idsServidores = List.copyOf(idsServidores);
    }

    public boolean esGrupal() {
        return modoEjecucion != null;
    }
}
