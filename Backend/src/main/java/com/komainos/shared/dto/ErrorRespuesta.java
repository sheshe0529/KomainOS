package com.komainos.shared.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** codigo es estable: el panel ramifica sobre él sin depender del texto del mensaje */
public record ErrorRespuesta(
        OffsetDateTime marcaTiempo,
        int estado,
        String codigo,
        String mensaje,
        String ruta,
        List<ErrorCampo> errores) {

    public record ErrorCampo(String campo, String mensaje) {
    }

    public static ErrorRespuesta de(int estado, String codigo, String mensaje, String ruta) {
        return new ErrorRespuesta(OffsetDateTime.now(), estado, codigo, mensaje, ruta, null);
    }

    public static ErrorRespuesta deValidacion(String ruta, List<ErrorCampo> errores) {
        return new ErrorRespuesta(
                OffsetDateTime.now(), 400, "VALIDACION",
                "La petición contiene campos inválidos", ruta, errores);
    }
}
