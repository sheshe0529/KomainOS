package com.komainos.alerta.controller;

import com.komainos.alerta.model.AlertaDestinatario;
import com.komainos.alerta.model.TipoAlerta;
import com.komainos.alerta.service.ServicioAlertas;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Alertas del usuario autenticado (RF69, HU30): todos los roles, siempre limitadas a sus destinatarios */
@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
@Tag(name = "Alertas")
public class AlertaController {

    private final ServicioAlertas servicio;

    @GetMapping
    @Operation(summary = "Alertas del usuario, de la más reciente a la más antigua")
    public PaginaRespuesta<AlertaRespuesta> listar(@AuthenticationPrincipal UsuarioAutenticado u,
                                                   @RequestParam(defaultValue = "false") boolean soloNoLeidas,
                                                   @PageableDefault(size = 20) Pageable pagina) {
        return PaginaRespuesta.de(servicio.listar(u.id(), soloNoLeidas, pagina), AlertaRespuesta::de);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle completo de una alerta del usuario")
    public AlertaRespuesta detalle(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        return AlertaRespuesta.de(servicio.obtener(id, u.id()));
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Cantidad de alertas sin leer")
    public Map<String, Long> pendientes(@AuthenticationPrincipal UsuarioAutenticado u) {
        return Map.of("pendientes", servicio.pendientes(u.id()));
    }

    @PostMapping("/{id}/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca una alerta como leída")
    public void leer(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        servicio.leer(id, u.id());
    }

    @PostMapping("/lectura")
    @Operation(summary = "Marca como leídas todas las alertas del usuario")
    public Map<String, Integer> leerTodas(@AuthenticationPrincipal UsuarioAutenticado u) {
        return Map.of("leidas", servicio.leerTodas(u.id()));
    }

    /** @param id identificador de la alerta para este destinatario, no de la alerta compartida */
    public record AlertaRespuesta(Integer id, TipoAlerta tipo, String titulo, String mensaje, Integer idServidor,
                                  Integer idGrupo, Integer idOrden, Integer idIncidencia, Instant fecha, boolean leida) {

        static AlertaRespuesta de(AlertaDestinatario d) {
            var a = d.getAlerta();
            return new AlertaRespuesta(d.getId(), a.getTipo(), a.getTitulo(), a.getMensaje(), a.getIdServidor(),
                    a.getIdGrupo(), a.getIdOrden(), a.getIdIncidencia(), a.getFechaGeneracion(), d.isLeida());
        }
    }
}
