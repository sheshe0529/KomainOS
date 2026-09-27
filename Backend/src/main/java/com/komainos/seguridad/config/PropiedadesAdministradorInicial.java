package com.komainos.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del primer administrador (DEC-21). Solo se usan cuando la base no
 * tiene ningun administrador activo; la clave viene del entorno y nunca se
 * versiona.
 */
@ConfigurationProperties(prefix = "komainos.seguridad.administrador-inicial")
public record PropiedadesAdministradorInicial(String codigo, String nombre, String clave) {

    public boolean tieneClave() {
        return clave != null && !clave.isBlank();
    }
}
