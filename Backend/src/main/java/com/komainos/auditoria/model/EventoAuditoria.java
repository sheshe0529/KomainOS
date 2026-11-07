package com.komainos.auditoria.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Vista de lectura de un evento: nunca incluye material de autenticación del usuario. */
public record EventoAuditoria(Integer id, Instant fechaHora, Integer idUsuario, String codigoUsuario,
                              String nombreUsuario, String proceso, String operacion, String entidad,
                              Integer idEntidad, Integer idServidor, Integer idGrupo, Integer idOrden,
                              String motivo, JsonNode valorAnterior, JsonNode valorNuevo) {
}
