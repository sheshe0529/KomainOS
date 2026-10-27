package com.komainos.ejecucion.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.util.List;

/**
 * Todo lo que el adaptador necesita para una operación remota, sin secretos: la cuenta va por identificador y el
 * adaptador real la resuelve en el último momento (RNF11).
 *
 * @param contenido  código de la versión fijada; vacío en una prueba de conectividad
 * @param maxMinutos tiempo máximo de la operación antes de cancelarla
 * @param referencia valores esperados de las métricas; solo los usa el modo simulado
 */
public record SolicitudEjecucion(Integer idServidor, String hostname, String direccionIp,
                                 FamiliaSistemaOperativo familia, Integer idCuentaServicio,
                                 PropositoEjecucion proposito, String contenido, List<ParametroEjecucion> parametros,
                                 int maxMinutos, List<MetricaReferencia> referencia) {

    public SolicitudEjecucion {
        contenido = contenido == null ? "" : contenido;
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        referencia = referencia == null ? List.of() : List.copyOf(referencia);
    }

    public static SolicitudEjecucion conectividad(Integer idServidor, String hostname, String direccionIp,
                                                  FamiliaSistemaOperativo familia, Integer idCuentaServicio,
                                                  int maxMinutos) {
        return new SolicitudEjecucion(idServidor, hostname, direccionIp, familia, idCuentaServicio,
                PropositoEjecucion.CONECTIVIDAD, "", List.of(), maxMinutos, List.of());
    }

    public boolean esAnalisis() {
        return proposito.esAnalisis();
    }
}
