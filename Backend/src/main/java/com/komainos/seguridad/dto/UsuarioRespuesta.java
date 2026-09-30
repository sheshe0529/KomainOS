package com.komainos.seguridad.dto;

import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;

import java.time.Instant;

/** Cuenta de usuario sin el hash de su contrasena (RNF11). */
public record UsuarioRespuesta(Integer id, String codigo, String nombreCompleto, Rol rol,
                               boolean activo, Instant fechaCreacion) {

    public static UsuarioRespuesta de(Usuario u) {
        return new UsuarioRespuesta(u.getId(), u.getCodigo(), u.getNombreCompleto(), u.getRol(),
                u.isActivo(), u.getFechaCreacion());
    }
}
