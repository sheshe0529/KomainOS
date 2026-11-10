package com.komainos.ejecucion.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.util.List;

/**
 * Todo lo que el adaptador necesita para una operación remota, sin secretos: la cuenta va por identificador y el
 * adaptador la resuelve en el último momento (RNF11).
 *
 * @param contenido    código de la versión fijada; vacío en una prueba de conectividad
 * @param maxMinutos   tiempo máximo de la operación antes de cancelarla
 * @param idEvaluacion evaluación que registra el resultado de un análisis; nulo en tareas, pruebas de acceso y pruebas
 *                     manuales de una versión
 * @param listas       las listas de referencia que rigen para el servidor; solo en los análisis
 */
public record SolicitudEjecucion(Integer idServidor, String hostname, String direccionIp,
                                 FamiliaSistemaOperativo familia, Integer idCuentaServicio,
                                 PropositoEjecucion proposito, String contenido, List<ParametroEjecucion> parametros,
                                 int maxMinutos, Integer idEvaluacion, List<ListaEjecucion> listas) {

    public SolicitudEjecucion {
        contenido = contenido == null ? "" : contenido;
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        listas = listas == null ? List.of() : List.copyOf(listas);
    }

    /** Una tarea de mantenimiento: solo recibe los parámetros de su versión */
    public static SolicitudEjecucion tarea(Integer idServidor, String hostname, String direccionIp,
                                           FamiliaSistemaOperativo familia, Integer idCuentaServicio, String contenido,
                                           List<ParametroEjecucion> parametros, int maxMinutos) {
        return new SolicitudEjecucion(idServidor, hostname, direccionIp, familia, idCuentaServicio,
                PropositoEjecucion.TAREA, contenido, parametros, maxMinutos, null, List.of());
    }

    public static SolicitudEjecucion conectividad(Integer idServidor, String hostname, String direccionIp,
                                                  FamiliaSistemaOperativo familia, Integer idCuentaServicio,
                                                  int maxMinutos) {
        return new SolicitudEjecucion(idServidor, hostname, direccionIp, familia, idCuentaServicio,
                PropositoEjecucion.CONECTIVIDAD, "", List.of(), maxMinutos, null, List.of());
    }

    public static SolicitudEjecucion conectividadEvaluacion(Integer idServidor, String hostname, String direccionIp,
                                                            FamiliaSistemaOperativo familia, Integer cuenta, int maxMinutos) {
        return new SolicitudEjecucion(idServidor, hostname, direccionIp, familia, cuenta,
                PropositoEjecucion.CONECTIVIDAD_EVALUACION, "", List.of(), maxMinutos, null, List.of());
    }

    public boolean esAnalisis() {
        return proposito.esAnalisis();
    }
}
