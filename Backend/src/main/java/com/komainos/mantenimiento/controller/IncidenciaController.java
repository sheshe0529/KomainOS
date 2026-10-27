package com.komainos.mantenimiento.controller;

import com.komainos.mantenimiento.dto.CicloPeticiones;
import com.komainos.mantenimiento.dto.IncidenciaRespuesta;
import com.komainos.mantenimiento.mapper.AutomatizacionMapeador;
import com.komainos.mantenimiento.model.EstadoIncidencia;
import com.komainos.mantenimiento.model.OrigenIncidencia;
import com.komainos.mantenimiento.service.ServicioIncidencias;
import com.komainos.mantenimiento.service.ServicioIncidencias.DatosAtencion;
import com.komainos.mantenimiento.service.ServicioIncidencias.FiltroIncidencias;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de incidencias (RF61, HU28): consultar, filtrar, atender y cerrar */
@RestController
@RequestMapping("/api/incidencias")
@RequiredArgsConstructor
@Tag(name = "Incidencias")
public class IncidenciaController {

    private final ServicioIncidencias servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Incidencias con filtros de estado, origen, servidor y texto, de la más reciente a la más antigua")
    public PaginaRespuesta<IncidenciaRespuesta> listar(@RequestParam(required = false) EstadoIncidencia estado,
                                                       @RequestParam(required = false) OrigenIncidencia origen,
                                                       @RequestParam(required = false) Integer idServidor,
                                                       @RequestParam(required = false) String texto,
                                                       @PageableDefault(size = 20) Pageable pagina) {
        return PaginaRespuesta.de(servicio.listar(new FiltroIncidencias(estado, origen, idServidor, texto), pagina),
                AutomatizacionMapeador::incidencia);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Detalle de una incidencia")
    public IncidenciaRespuesta obtener(@PathVariable Integer id) {
        return AutomatizacionMapeador.incidencia(servicio.obtener(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra la atención; al cerrar la última, la orden continúa a su cierre sin reanudar tareas")
    public IncidenciaRespuesta atender(
            @PathVariable Integer id, @Valid @RequestBody CicloPeticiones.AtenderIncidenciaPeticion p,
            @AuthenticationPrincipal UsuarioAutenticado u) {
        servicio.atender(id, new DatosAtencion(p.diagnostico(), p.acciones(), p.resultado(), p.cerrar()),
                AlcanceUsuario.de(u).actor());
        return AutomatizacionMapeador.incidencia(servicio.obtener(id));
    }
}
