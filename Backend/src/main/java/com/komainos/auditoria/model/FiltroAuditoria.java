package com.komainos.auditoria.model;

import com.komainos.shared.exception.ReglaNegocioException;

import java.time.Instant;

/** RNF06: el fin es exclusivo; Sistema comprende los procesos sin usuario humano. */
public record FiltroAuditoria(String texto, Integer idUsuario, Boolean sistema, String operacion,
                              String entidad, Instant desde, Instant hasta) {
    public FiltroAuditoria {
        texto = limpiar(texto, 200);
        operacion = limpiar(operacion, 255);
        entidad = limpiar(entidad, 255);
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            throw new ReglaNegocioException("El inicio del periodo debe ser anterior al final");
        }
        if (idUsuario != null && (idUsuario < 1 || Boolean.TRUE.equals(sistema))) {
            throw new ReglaNegocioException("Seleccione un usuario o los procesos del sistema");
        }
    }

    private static String limpiar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) return null;
        String limpio = texto.trim();
        if (limpio.length() > maximo) {
            throw new ReglaNegocioException("El filtro supera los %d caracteres permitidos".formatted(maximo));
        }
        return limpio;
    }
}
