package com.komainos.inventario.api.dto;

import com.komainos.inventario.api.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.dominio.EstadoGrupo;
import com.komainos.inventario.dominio.ModalidadPlanificacion;
import com.komainos.inventario.dominio.ModoEjecucion;
import com.komainos.shared.api.ReferenciaSimple;

/**
 * Grupo en el listado. Entorno, responsable y sistema operativo son los
 * comunes a todos sus integrantes (RF20); la criticidad es la mas alta de
 * ellos (RF76). Son nulos si el grupo no tiene integrantes.
 */
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
