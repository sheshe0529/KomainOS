package com.komainos.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "komainos.seguridad.administrador-inicial")
public record PropiedadesAdministradorInicial(String codigo, String nombre, String clave) {

    public boolean tieneClave() {
        return clave != null && !clave.isBlank();
    }
}
