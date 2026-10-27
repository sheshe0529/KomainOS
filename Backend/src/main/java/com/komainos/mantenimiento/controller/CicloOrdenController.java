package com.komainos.mantenimiento.controller;

import com.komainos.mantenimiento.dto.CicloPeticiones;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta;
import com.komainos.mantenimiento.mapper.AutomatizacionMapeador;
import com.komainos.mantenimiento.service.ServicioAutorizaciones;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion;
import com.komainos.mantenimiento.service.ServicioEvaluacionPrevia;
import com.komainos.mantenimiento.service.ServicioValidaciones;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** El ciclo de una orden: evaluación, autorización, validación y bitácora (RF38-RF57, HU25-HU27) */
@RestController
@RequestMapping("/api/ordenes/{id}")
@RequiredArgsConstructor
@Tag(name = "Ciclo de mantenimiento")
public class CicloOrdenController {

    private final ServicioConsultaAutomatizacion consulta;
    private final ServicioEvaluacionPrevia evaluacion;
    private final ServicioAutorizaciones autorizaciones;
    private final ServicioValidaciones validaciones;

    @GetMapping("/automatizacion")
    @Operation(summary = "Bitácora de la orden: MOP, análisis, autorizaciones, validaciones, incidencias y reporte comparativo")
    public FichaAutomatizacionRespuesta ficha(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        return AutomatizacionMapeador.ficha(consulta.ficha(id, AlcanceUsuario.de(u)));
    }

    /** Los errores de configuración responden al instante; los análisis continúan en segundo plano */
    @PostMapping("/evaluacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Inicia la evaluación previa de una orden programada")
    public Map<String, String> evaluar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        evaluacion.iniciar(id, AlcanceUsuario.de(u).actor());
        return Map.of("mensaje", "Evaluación previa iniciada: los servidores se están analizando");
    }

    @PostMapping("/autorizacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'RESPONSABLE')")
    @Operation(summary = "Aprueba, rechaza o propone otra fecha en el turno del usuario (RF44, RF47, RF48)")
    public void decidir(@PathVariable Integer id, @Valid @RequestBody CicloPeticiones.DecidirAutorizacionPeticion p,
                        @AuthenticationPrincipal UsuarioAutenticado u) {
        autorizaciones.decidir(id, p.decision(), p.motivo(), p.fechaPropuesta(), AlcanceUsuario.de(u));
    }

    @PostMapping("/aceptar-fecha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Acepta la fecha propuesta por el responsable y abre una nueva ronda de aprobaciones")
    public void aceptarFecha(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado u) {
        autorizaciones.aceptarPropuesta(id, AlcanceUsuario.de(u).actor());
    }

    @PostMapping("/validacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RESPONSABLE')")
    @Operation(summary = "Registra la conformidad o el rechazo del responsable (RF57)")
    public void validar(@PathVariable Integer id, @Valid @RequestBody CicloPeticiones.ValidarOrdenPeticion p,
                        @AuthenticationPrincipal UsuarioAutenticado u) {
        validaciones.validar(id, p.conforme(), p.observaciones(), AlcanceUsuario.de(u));
    }

    @PostMapping("/cierre-sin-implementacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cierra sin intervención una orden bloqueada por falta de implementación (RF42)")
    public void cerrar(@PathVariable Integer id, @Valid @RequestBody CicloPeticiones.MotivoPeticion p,
                       @AuthenticationPrincipal UsuarioAutenticado u) {
        evaluacion.cerrarSinImplementacion(id, p.motivo(), AlcanceUsuario.de(u).actor());
    }
}
