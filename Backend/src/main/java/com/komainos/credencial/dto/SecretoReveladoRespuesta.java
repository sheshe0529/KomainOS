package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.model.TipoUsuario;

/** Única respuesta que lleva secretos en claro: viaja sin caché y el panel los oculta al vencer (RF08, HU05 CA2) */
public record SecretoReveladoRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        TipoAutenticacion tipoAutenticacion,
        TipoUsuario tipoUsuario,
        Integer numeroVersion,
        String secreto,
        /** Contraseña su, solo con un usuario Genérico */
        String secretoSu,
        int segundosVisible) {
}
