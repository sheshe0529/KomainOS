package com.komainos.mantenimiento.controller;

import com.komainos.mantenimiento.dto.PoliticaCambioPeticion;
import com.komainos.mantenimiento.dto.PoliticaCambioRespuesta;
import com.komainos.mantenimiento.mapper.AutomatizacionMapeador;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import com.komainos.mantenimiento.service.ServicioPoliticasCambio;
import com.komainos.mantenimiento.service.ServicioPoliticasCambio.DatosRegla;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Política de gestión del cambio por entorno y afectación (RF43, HU24) */
@RestController
@RequestMapping("/api/politicas-cambio")
@RequiredArgsConstructor
@Tag(name = "Política de gestión del cambio")
public class PoliticaCambioController {

    private final ServicioPoliticasCambio servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Reglas de la política con sus notificaciones")
    public PaginaRespuesta<PoliticaCambioRespuesta> listar(@PageableDefault(size = 100) Pageable pagina) {
        return PaginaRespuesta.de(servicio.listar(pagina), this::respuesta);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra la regla de una combinación de entorno y afectación")
    public PoliticaCambioRespuesta registrar(@Valid @RequestBody PoliticaCambioPeticion p,
                                             @AuthenticationPrincipal UsuarioAutenticado u) {
        return respuesta(servicio.guardar(null, datos(p), AlcanceUsuario.de(u).actor()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica una regla; las rondas de autorización ya abiertas no cambian")
    public PoliticaCambioRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody PoliticaCambioPeticion p,
                                              @AuthenticationPrincipal UsuarioAutenticado u) {
        return respuesta(servicio.guardar(id, datos(p), AlcanceUsuario.de(u).actor()));
    }

    private PoliticaCambioRespuesta respuesta(ReglaPoliticaCambio regla) {
        return AutomatizacionMapeador.politica(regla, servicio.notificaciones(regla.getId()));
    }

    private static DatosRegla datos(PoliticaCambioPeticion p) {
        return new DatosRegla(p.idEntorno(), p.nivelAfectacion(), p.requiereResponsable(), p.requiereAdministrador(),
                p.activo(), p.notificaciones(), p.versionEsperada());
    }
}
