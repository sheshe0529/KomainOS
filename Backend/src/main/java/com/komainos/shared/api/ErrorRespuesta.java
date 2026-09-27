package com.komainos.shared.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Forma unica de todo error que sale del backend.
 *
 * <p>Que sea una sola forma le permite al cliente de API del panel manejar los
 * errores en un solo lugar en vez de interpretar un cuerpo distinto por
 * endpoint. Los mensajes van en espanol porque se muestran al usuario (RNF05).
 *
 * @param codigo identificador estable del tipo de error; el frontend puede
 *               ramificar sobre el sin depender del texto del mensaje.
 */
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
