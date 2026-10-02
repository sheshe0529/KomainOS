package com.komainos.seguridad.dto;

import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.UsuarioAutenticado;

public record UsuarioSesion(Integer id, String codigo, String nombreCompleto, Rol rol) {

    public static UsuarioSesion de(UsuarioAutenticado autenticado) {
        return new UsuarioSesion(
                autenticado.id(),
                autenticado.getUsername(),
                autenticado.usuario().getNombreCompleto(),
                autenticado.rol());
    }
}
