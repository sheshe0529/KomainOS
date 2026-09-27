package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.Entorno;

public record EntornoRespuesta(Integer id, String nombre, String descripcion, boolean activo) {

    public static EntornoRespuesta de(Entorno e) {
        return new EntornoRespuesta(e.getId(), e.getNombre(), e.getDescripcion(), e.isActivo());
    }
}
