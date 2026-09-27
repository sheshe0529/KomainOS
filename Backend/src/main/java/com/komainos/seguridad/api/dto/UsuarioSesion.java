package com.komainos.seguridad.api.dto;

import com.komainos.seguridad.dominio.Rol;
import com.komainos.seguridad.dominio.UsuarioAutenticado;

/**
 * Identidad que el panel necesita para armar el menu segun el rol (HU01 CA3).
 * No incluye el hash de la contrasena.
 */
public record UsuarioSesion(Integer id, String codigo, String nombreCompleto, Rol rol) {

    public static UsuarioSesion de(UsuarioAutenticado autenticado) {
        return new UsuarioSesion(
                autenticado.id(),
                autenticado.getUsername(),
                autenticado.usuario().getNombreCompleto(),
                autenticado.rol());
    }
}
