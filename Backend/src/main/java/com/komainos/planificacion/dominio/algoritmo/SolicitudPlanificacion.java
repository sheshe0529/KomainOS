package com.komainos.planificacion.dominio.algoritmo;

import com.komainos.inventario.dominio.ModoEjecucion;
import com.komainos.inventario.dominio.ventana.CalendarioSemanal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Entradas del algoritmo voraz (especificacion, seccion 2).
 *
 * @param idsServidores       servidores del objetivo, en su orden de posicion
 *                            (uno para una orden individual)
 * @param modoEjecucion       modo del grupo; nulo en ordenes individuales
 * @param fechaObjetivo       T0 (RF64)
 * @param plazoAutorizacion   P: anticipacion de la evaluacion previa (RF38)
 * @param ventana             ventana permisiva efectiva W (RF21 en grupos)
 * @param duracionPorServidor D: tiempo reservado por servidor (DEC-08)
 * @param capacidad           C: servidores en ejecucion simultanea (RF68)
 * @param ahora               instante actual
 * @param zona                zona horaria operativa de las ventanas (DEC-06)
 * @param horizonte           H: limite de busqueda (DEC-12)
 */
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
