package com.komainos.seguridad.dto;

import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.UsuarioAutenticado;

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
