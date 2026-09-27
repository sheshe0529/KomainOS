package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.NivelCriticidad;

public record NivelCriticidadRespuesta(Integer id, String nombre, Integer prioridad,
                                       Integer frecuenciaRevisionDias, Integer frecuenciaMantenimientoDias,
                                       Integer plazoAutorizacionHoras, Integer plazoValidacionHoras,
                                       boolean activo) {

    public static NivelCriticidadRespuesta de(NivelCriticidad n) {
        return new NivelCriticidadRespuesta(n.getId(), n.getNombre(), n.getPrioridad(),
                n.getFrecuenciaRevisionDias(), n.getFrecuenciaMantenimientoDias(),
                n.getPlazoAutorizacionHoras(), n.getPlazoValidacionHoras(), n.isActivo());
    }
}
