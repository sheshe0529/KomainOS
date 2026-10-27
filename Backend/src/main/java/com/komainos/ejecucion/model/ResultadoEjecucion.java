package com.komainos.ejecucion.model;

import java.util.List;

/**
 * Resultado de una operación remota, ya saneado: ni la salida ni el error contienen secretos (RNF11).
 *
 * @param codigoRetorno nulo si el script no llegó a ejecutarse (comunicación, autenticación, configuración)
 * @param fallo         nulo si la operación terminó bien
 * @param metricas      solo en análisis correctos
 */
public record ResultadoEjecucion(boolean simulado, Integer codigoRetorno, String salida, String error,
                                 TipoFallo fallo, List<MedicionInformada> metricas) {

    public ResultadoEjecucion {
        salida = salida == null ? "" : salida;
        metricas = metricas == null ? List.of() : List.copyOf(metricas);
    }

    public static ResultadoEjecucion exito(boolean simulado, String salida, List<MedicionInformada> metricas) {
        return new ResultadoEjecucion(simulado, 0, salida, null, null, metricas);
    }

    /** El script se ejecutó y devolvió un código distinto de cero */
    public static ResultadoEjecucion codigoError(boolean simulado, int codigo, String salida, String error) {
        String detalle = error == null || error.isBlank()
                ? "el script terminó con el código " + codigo
                : error;
        return new ResultadoEjecucion(simulado, codigo, salida, TipoFallo.EJECUCION.describir(detalle),
                TipoFallo.EJECUCION, List.of());
    }

    public static ResultadoEjecucion fallo(boolean simulado, TipoFallo tipo, String detalle) {
        return new ResultadoEjecucion(simulado, null, "", tipo.describir(detalle), tipo, List.of());
    }

    public boolean correcto() {
        return fallo == null;
    }
}
