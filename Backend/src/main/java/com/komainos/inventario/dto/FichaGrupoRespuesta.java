package com.komainos.inventario.dto;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.model.EstadoGrupo;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;
import java.util.List;

public record FichaGrupoRespuesta(
        Integer id,
        String nombre,
        String descripcion,
        EstadoGrupo estado,
        CriticidadResumen criticidad,
        ReferenciaSimple entorno,
        ReferenciaSimple responsable,
        ReferenciaSimple sistemaOperativo,
        ConfiguracionRespuesta configuracion,
        List<ServidorResumenRespuesta> integrantes,
        List<VentanaRespuesta> ventanaEfectiva,
        Instant fechaCreacion,
        Instant fechaActualizacion) {
}
