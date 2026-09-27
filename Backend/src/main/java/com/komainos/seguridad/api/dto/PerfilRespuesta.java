package com.komainos.seguridad.api.dto;

import com.komainos.seguridad.dominio.Rol;
import com.komainos.seguridad.dominio.Usuario;

import java.time.Instant;

/**
 * Detalle de la cuenta del usuario autenticado ("Mi cuenta"). Nunca incluye
 * el hash de la contrasena (RNF11).
 *
 * @param servidoresACargo servidores no dados de baja de los que es responsable;
 *                         solo para el rol Responsable
 * @param minutosSesion    vigencia de la sesion configurada (RF02)
 */
public record PerfilRespuesta(Integer id, String codigo, String nombreCompleto, Rol rol, boolean activo,
                              Instant fechaCreacion, Instant fechaActualizacion, Long servidoresACargo,
                              Integer minutosSesion) {

    public static PerfilRespuesta de(Usuario u, Long servidoresACargo, Integer minutosSesion) {
        return new PerfilRespuesta(u.getId(), u.getCodigo(), u.getNombreCompleto(), u.getRol(), u.isActivo(),
                u.getFechaCreacion(), u.getFechaActualizacion(), servidoresACargo, minutosSesion);
    }
}
