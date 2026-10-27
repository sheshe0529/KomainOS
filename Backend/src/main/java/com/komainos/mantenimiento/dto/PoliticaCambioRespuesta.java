package com.komainos.mantenimiento.dto;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.mantenimiento.model.TipoDestinatario;

import java.time.Instant;
import java.util.List;

public record PoliticaCambioRespuesta(Integer id, Integer idEntorno, String entorno, NivelAfectacion nivelAfectacion,
                                      boolean requiereResponsable, boolean requiereAdministrador, boolean activo,
                                      Instant fechaActualizacion, List<TipoDestinatario> notificaciones) {
}
