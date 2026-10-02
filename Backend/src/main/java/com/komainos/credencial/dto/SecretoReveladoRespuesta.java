package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;

/** Única respuesta que lleva un secreto en claro: viaja sin caché y el panel lo oculta al vencer (RF08, HU05 CA2) */
public record SecretoReveladoRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        TipoAutenticacion tipoAutenticacion,
        Integer numeroVersion,
        String secreto,
        int segundosVisible) {
}
