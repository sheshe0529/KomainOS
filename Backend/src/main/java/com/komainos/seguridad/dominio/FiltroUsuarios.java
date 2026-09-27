package com.komainos.seguridad.dominio;

/**
 * Criterios de busqueda de usuarios. Cada campo es opcional.
 *
 * @param texto coincidencia parcial contra codigo o nombre completo
 */
public record FiltroUsuarios(String texto, Rol rol, Boolean activo) {
}
