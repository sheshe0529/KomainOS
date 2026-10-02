package com.komainos.inventario.dto;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.model.EstadoGrupo;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.shared.dto.ReferenciaSimple;

public record GrupoResumenRespuesta(
        Integer id,
        String nombre,
        String descripcion,
        EstadoGrupo estado,
        int cantidadIntegrantes,
        CriticidadResumen criticidad,
        ReferenciaSimple entorno,
        ReferenciaSimple responsable,
        ReferenciaSimple sistemaOperativo,
        ModalidadPlanificacion modalidadPlanificacion,
        ModoEjecucion modoEjecucion) {
}
