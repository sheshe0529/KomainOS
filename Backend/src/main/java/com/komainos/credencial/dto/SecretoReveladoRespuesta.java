package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.ejecucion.model.MetodoElevacion;

import java.time.Instant;

/** Única respuesta que lleva secretos en claro: viaja sin caché y el panel los oculta al vencer (RF08, HU05 CA2) */
public record SecretoReveladoRespuesta(
        Integer id,
        String nombre,
        String usuarioAcceso,
        TipoAutenticacion tipoAutenticacion,
        MetodoElevacion metodoElevacion,
        /** Registro o última rotación del secreto revelado */
        Instant fechaSecreto,
        String secreto,
        /** Contraseña de sudo o de root para su; nula si la elevación no la lleva */
        String secretoElevacion,
        int segundosVisible) {

    @Override
    public String toString() {
        return "SecretoReveladoRespuesta[id=%s, tipoAutenticacion=%s]".formatted(id, tipoAutenticacion);
    }
}
