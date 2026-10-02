package com.komainos.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Orígenes explícitos y no comodín: con un origen abierto cualquier página podría consultar la API desde el navegador del usuario */
@ConfigurationProperties(prefix = "komainos.seguridad.cors")
public record PropiedadesCors(List<String> origenes) {
}
