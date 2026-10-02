package com.komainos.credencial.dto;

import com.komainos.credencial.model.EstadoCredencial;
import com.komainos.credencial.model.TipoAutenticacion;

import java.time.Instant;

/** Cuenta de servicio con su uso: configuraciones que la tienen asignada y si es la predeterminada (RF05, RF06) */
public record CuentaServicioRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        String descripcion,
        EstadoCredencial estado,
        TipoAutenticacion tipoAutenticacion,
        Integer numeroVersion,
        Instant fechaRegistro,
        Instant fechaSecreto,
        Instant fechaRevocacion,
        long servidores,
        long grupos,
        boolean predeterminada) {
}
