package com.komainos.credencial.dto;

import com.komainos.credencial.model.EstadoCredencial;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.ejecucion.model.MetodoElevacion;

import java.time.Instant;

/** Datos identificativos sin el secreto ni su forma cifrada (RF04, RNF11) */
public record CredencialRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        String descripcion,
        EstadoCredencial estado,
        TipoAutenticacion tipoAutenticacion,
        /** Cómo obtiene privilegios Ansible (DEC-57); nulo si la credencial está revocada */
        MetodoElevacion metodoElevacion,
        /** Si el secreto vigente guarda la contraseña que pide sudo o su */
        boolean conContrasenaElevacion,
        /** Solo en credenciales documentales: la que viaja en el inventario */
        Boolean principal,
        Instant fechaRegistro,
        /** Registro o última rotación del secreto vigente; nula si la credencial está revocada */
        Instant fechaSecreto,
        Instant fechaRevocacion) {
}
