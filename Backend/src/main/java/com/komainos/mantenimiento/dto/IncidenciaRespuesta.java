package com.komainos.mantenimiento.dto;

import com.komainos.mantenimiento.model.EstadoIncidencia;
import com.komainos.mantenimiento.model.OrigenIncidencia;

import java.time.Instant;

/** Incidencia con servidor, orden, etapa de origen, error y atención (RF61, RF62) */
public record IncidenciaRespuesta(Integer id, Integer idOrden, String orden, Integer idServidor, String servidor,
                                  OrigenIncidencia origen, EstadoIncidencia estado, String error, String diagnostico,
                                  String acciones, String resultado, String atendidaPor, Instant fechaApertura,
                                  Instant fechaCierre) {
}
