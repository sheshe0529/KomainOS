package com.komainos.inventario.api.dto;

import com.komainos.inventario.api.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.dominio.EstadoGrupo;
import com.komainos.shared.api.ReferenciaSimple;

import java.time.Instant;
import java.util.List;

/**
 * Detalle del grupo (HU15): integrantes, configuracion, criticidad efectiva
 * (RF76) y ventana permisiva calculada como interseccion (RF21).
 */
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
