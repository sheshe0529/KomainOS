package com.komainos.planificacion.api;

import com.komainos.mantenimiento.api.OrdenMapeador;
import com.komainos.mantenimiento.api.dto.OrdenDetalleRespuesta;
import com.komainos.mantenimiento.api.dto.OrdenResumenRespuesta;
import com.komainos.mantenimiento.dominio.ServicioConsultaOrdenes;
import com.komainos.planificacion.api.dto.CancelarOrdenPeticion;
import com.komainos.planificacion.api.dto.ProgramarOrdenPeticion;
import com.komainos.planificacion.api.dto.PropuestaRespuesta;
import com.komainos.planificacion.api.dto.ReprogramarOrdenPeticion;
import com.komainos.planificacion.api.dto.ResumenPlanificacionRespuesta;
import com.komainos.planificacion.dominio.ServicioCronograma;
import com.komainos.planificacion.dominio.ServicioPlanificacion;
import com.komainos.seguridad.dominio.AlcanceUsuario;
import com.komainos.seguridad.dominio.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Planificacion y gestion del cronograma (RF27-RF30, RF32).
 *
 * <p>Programar, reprogramar y cancelar es exclusivo del administrador (RF30);
 * el cronograma lo consultan todos los roles dentro de su alcance (RF32).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Planificación y cronograma")
public class PlanificacionController {

    private final ServicioPlanificacion planificacion;
    private final ServicioCronograma cronograma;
    private final ServicioConsultaOrdenes consultaOrdenes;

    @GetMapping("/cronograma")
    @Operation(summary = "Órdenes programadas en un rango, dentro del alcance del usuario (RF32)")
    public List<OrdenResumenRespuesta> cronograma(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return cronograma.entradas(desde.toInstant(), hasta.toInstant(), AlcanceUsuario.de(solicitante)).stream()
                .map(p -> OrdenMapeador.resumen(p.getOrden(), Optional.of(p)))
                .toList();
    }

    @PostMapping("/ordenes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Programa una orden para un servidor o grupo (RF30)")
    public OrdenDetalleRespuesta programar(@Valid @RequestBody ProgramarOrdenPeticion peticion,
                                           @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        Integer id = planificacion.programar(peticion.idServidor(), peticion.idGrupo(),
                peticion.inicio() == null ? null : peticion.inicio().toInstant(), peticion.motivo(), alcance.actor()).getId();
        return OrdenMapeador.detalle(consultaOrdenes.obtener(id, alcance));
    }

    @PostMapping("/ordenes/{id}/reprogramacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reprograma una orden conservando la programación anterior (RF30)")
    public OrdenDetalleRespuesta reprogramar(@PathVariable Integer id, @Valid @RequestBody ReprogramarOrdenPeticion peticion,
                                             @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        planificacion.reprogramar(id, peticion.inicio() == null ? null : peticion.inicio().toInstant(),
                peticion.motivo().trim(), alcance.actor());
        return OrdenMapeador.detalle(consultaOrdenes.obtener(id, alcance));
    }

    @PostMapping("/ordenes/{id}/cancelacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cancela una orden con motivo (RF30)")
    public OrdenDetalleRespuesta cancelar(@PathVariable Integer id, @Valid @RequestBody CancelarOrdenPeticion peticion,
                                          @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        planificacion.cancelar(id, peticion.motivo().trim(), alcance.actor());
        return OrdenMapeador.detalle(consultaOrdenes.obtener(id, alcance));
    }

    @GetMapping("/planificacion/propuesta")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Primer intervalo disponible para un servidor o grupo, sin crear la orden (RF28)")
    public PropuestaRespuesta proponer(
            @RequestParam(required = false) Integer idServidor,
            @RequestParam(required = false) Integer idGrupo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde) {
        return PropuestaRespuesta.de(planificacion.proponer(idServidor, idGrupo, desde == null ? null : desde.toInstant()));
    }

    @PostMapping("/planificacion/ejecuciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Ejecuta ahora la planificación automática de los ciclos pendientes (RF27, RF29)")
    public ResumenPlanificacionRespuesta ejecutarPlanificacion() {
        return ResumenPlanificacionRespuesta.de(planificacion.planificarCiclosAutomaticos());
    }
}
