package com.komainos.mantenimiento.controller;

import com.komainos.mantenimiento.dto.OrdenDetalleRespuesta;
import com.komainos.mantenimiento.dto.OrdenResumenRespuesta;
import com.komainos.mantenimiento.mapper.OrdenMapeador;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.FiltroOrdenes;
import com.komainos.mantenimiento.service.ServicioConsultaOrdenes;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Consulta de ordenes y su detalle (RF33, RF36, HU23). Todos los roles
 * consultan dentro de su alcance: la ficha del servidor muestra su historial
 * de ordenes (HU10 CA4) y el cronograma permite abrir cada orden (HU20 CA4).
 */
@RestController
@RequestMapping("/api/ordenes")
@RequiredArgsConstructor
@Tag(name = "Órdenes de mantenimiento")
public class OrdenController {

    private final ServicioConsultaOrdenes servicio;

    @GetMapping
    @Operation(summary = "Consulta órdenes por código, servidor, grupo, estado, criticidad y fechas (RF36)")
    public PaginaRespuesta<OrdenResumenRespuesta> listar(
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) Integer idServidor,
            @RequestParam(required = false) Integer idGrupo,
            @RequestParam(required = false) EstadoOrden estado,
            @RequestParam(required = false) Integer idNivelCriticidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable paginacion,
            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        var filtro = new FiltroOrdenes(codigo, idServidor, idGrupo, estado, idNivelCriticidad,
                desde == null ? null : desde.toInstant(), hasta == null ? null : hasta.toInstant());
        return PaginaRespuesta.de(servicio.listar(filtro, AlcanceUsuario.de(solicitante), paginacion),
                OrdenMapeador::resumen);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una orden con sus servidores, programaciones e historial (RF33)")
    public OrdenDetalleRespuesta detalle(@PathVariable Integer id,
                                         @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return OrdenMapeador.detalle(servicio.obtener(id, AlcanceUsuario.de(solicitante)));
    }
}
