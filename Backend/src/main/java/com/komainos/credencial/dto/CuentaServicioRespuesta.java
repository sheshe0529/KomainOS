package com.komainos.credencial.dto;

import com.komainos.credencial.model.EstadoCredencial;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.ejecucion.model.MetodoElevacion;

import java.time.Instant;

/** Cuenta de servicio con su uso: configuraciones que la tienen asignada y si es la predeterminada (RF05, RF06) */
public record CuentaServicioRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        String descripcion,
        EstadoCredencial estado,
        TipoAutenticacion tipoAutenticacion,
        /** Cómo obtiene privilegios Ansible en todas sus ejecuciones (DEC-57) */
        MetodoElevacion metodoElevacion,
        boolean conContrasenaElevacion,
        Instant fechaRegistro,
        Instant fechaSecreto,
        Instant fechaRevocacion,
        long servidores,
        long grupos,
        boolean predeterminadaEvaluacion,
        boolean predeterminadaMantenimiento) {
}
