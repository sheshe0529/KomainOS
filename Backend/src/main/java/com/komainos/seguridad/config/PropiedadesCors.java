package com.komainos.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origenes autorizados a consumir la API. Se listan explicitamente en vez de
 * usar comodin porque la API viaja con token en cabecera y un origen abierto
 * permitiria a cualquier pagina consultarla desde el navegador del usuario.
 */
@ConfigurationProperties(prefix = "komainos.seguridad.cors")
public record PropiedadesCors(List<String> origenes) {
}
