package com.komainos.auditoria.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.komainos.auditoria.model.EventoAuditoria;

import java.time.Instant;

public record AuditoriaRespuesta(Integer id, Instant fechaHora, Integer idUsuario, String codigoUsuario,
                                 String actor, String proceso, String operacion, String entidad, Integer idEntidad,
                                 Integer idServidor, Integer idGrupo, Integer idOrden, String motivo,
                                 JsonNode valorAnterior, JsonNode valorNuevo) {
    public static AuditoriaRespuesta de(EventoAuditoria e) {
        return new AuditoriaRespuesta(e.id(), e.fechaHora(), e.idUsuario(), e.codigoUsuario(),
                e.idUsuario() == null ? "Sistema" : e.nombreUsuario(), e.proceso(), e.operacion(), e.entidad(),
                e.idEntidad(), e.idServidor(), e.idGrupo(), e.idOrden(), e.motivo(), e.valorAnterior(), e.valorNuevo());
    }
}
