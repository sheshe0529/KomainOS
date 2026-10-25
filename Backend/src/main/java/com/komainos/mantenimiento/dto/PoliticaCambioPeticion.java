package com.komainos.mantenimiento.dto;
import jakarta.validation.constraints.*;import com.komainos.catalogo.model.NivelAfectacion;import com.komainos.mantenimiento.model.TipoDestinatario;import java.time.Instant;import java.util.Set;
public record PoliticaCambioPeticion(@NotNull Integer idEntorno,@NotNull NivelAfectacion nivelAfectacion,boolean requiereResponsable,boolean requiereAdministrador,boolean activo,@NotNull Set<@NotNull TipoDestinatario> notificaciones,Instant versionEsperada){}
