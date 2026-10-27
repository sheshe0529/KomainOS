package com.komainos.mantenimiento.dto;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.mantenimiento.model.TipoDestinatario;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

/** Celda de la política (RF43): sin aprobadores, la autorización es automática */
public record PoliticaCambioPeticion(
        @NotNull(message = "Indique el entorno de la regla")
        Integer idEntorno,

        @NotNull(message = "Indique la afectación de la regla")
        NivelAfectacion nivelAfectacion,

        boolean requiereResponsable,

        boolean requiereAdministrador,

        boolean activo,

        @NotNull(message = "Indique los roles a notificar, aunque la lista esté vacía")
        Set<@NotNull TipoDestinatario> notificaciones,

        /** Al editar, la fechaActualizacion que leyó el panel: si otro la cambió después, responde 409 */
        Instant versionEsperada) {
}
