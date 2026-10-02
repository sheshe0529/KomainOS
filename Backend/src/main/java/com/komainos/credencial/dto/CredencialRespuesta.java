package com.komainos.credencial.dto;

import com.komainos.credencial.model.EstadoCredencial;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.model.TipoUsuario;

import java.time.Instant;

/** Datos identificativos sin el secreto ni su forma cifrada (RF04, RNF11) */
public record CredencialRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        String descripcion,
        EstadoCredencial estado,
        TipoAutenticacion tipoAutenticacion,
        TipoUsuario tipoUsuario,
        /** Si la versión vigente guarda contraseña su */
        boolean conSu,
        /** Solo en credenciales documentales: la que viaja en el inventario */
        Boolean principal,
        Integer numeroVersion,
        Instant fechaRegistro,
        /** Fecha de la versión vigente del secreto */
        Instant fechaSecreto,
        Instant fechaRevocacion) {
}
