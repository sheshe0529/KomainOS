package com.komainos.credencial.dto;

import com.komainos.credencial.model.EstadoCredencial;
import com.komainos.credencial.model.TipoAutenticacion;

import java.time.Instant;

/** Datos identificativos sin el secreto ni su forma cifrada (RF04, RNF11) */
public record CredencialRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        String descripcion,
        EstadoCredencial estado,
        TipoAutenticacion tipoAutenticacion,
        Integer numeroVersion,
        Instant fechaRegistro,
        /** Fecha de la versión vigente del secreto */
        Instant fechaSecreto,
        Instant fechaRevocacion) {
}
