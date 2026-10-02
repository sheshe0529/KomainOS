package com.komainos.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** secreto: llave HMAC en base64 de al menos 32 bytes (HS256), viene del entorno (RNF03) */
@ConfigurationProperties(prefix = "komainos.seguridad.jwt")
public record PropiedadesJwt(String secreto, long expiracionMinutos) {
}
