package com.komainos.inventario.dto;

import com.komainos.inventario.model.Entorno;

public record EntornoRespuesta(Integer id, String nombre, String descripcion, boolean activo) {

    public static EntornoRespuesta de(Entorno e) {
        return new EntornoRespuesta(e.getId(), e.getNombre(), e.getDescripcion(), e.isActivo());
    }
}
