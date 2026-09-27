package com.komainos.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secreto            llave HMAC en base64; debe decodificar a 32 bytes
 *                           o mas para HS256. Viene del entorno (RNF03).
 * @param expiracionMinutos  vida del token; al vencer, RF02 exige volver a
 *                           autenticarse antes de atender otra solicitud.
 */
@ConfigurationProperties(prefix = "komainos.seguridad.jwt")
public record PropiedadesJwt(String secreto, long expiracionMinutos) {
}
