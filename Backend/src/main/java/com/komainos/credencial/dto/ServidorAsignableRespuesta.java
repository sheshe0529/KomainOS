package com.komainos.credencial.dto;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

/** Servidor con configuración de mantenimiento, idCuentaServicio nula si no tiene cuenta asignada (RF07) */
public record ServidorAsignableRespuesta(
        Integer idServidor,
        String hostname,
        String direccionIp,
        FamiliaSistemaOperativo familia,
        Integer idCuentaServicioEvaluacion, Integer idCuentaServicioMantenimiento) {
}
