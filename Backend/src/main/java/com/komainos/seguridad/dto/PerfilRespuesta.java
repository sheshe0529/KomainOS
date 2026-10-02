package com.komainos.seguridad.dto;

import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;

import java.time.Instant;

public record PerfilRespuesta(Integer id, String codigo, String nombreCompleto, Rol rol, boolean activo,
                              Instant fechaCreacion, Instant fechaActualizacion, Long servidoresACargo,
                              Integer minutosSesion) {

    public static PerfilRespuesta de(Usuario u, Long servidoresACargo, Integer minutosSesion) {
        return new PerfilRespuesta(u.getId(), u.getCodigo(), u.getNombreCompleto(), u.getRol(), u.isActivo(),
                u.getFechaCreacion(), u.getFechaActualizacion(), servidoresACargo, minutosSesion);
    }
}
