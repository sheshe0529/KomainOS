package com.komainos.inventario.controller;

import com.komainos.inventario.dto.ActualizarMetricaPeticion;
import com.komainos.inventario.dto.MetricaPeticion;
import com.komainos.inventario.dto.MetricaRespuesta;
import com.komainos.inventario.service.ServicioMetricas;
import com.komainos.inventario.service.ServicioMetricas.DatosMetrica;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metricas")
@RequiredArgsConstructor
@Tag(name = "Métricas")
public class MetricaController {

    private final ServicioMetricas servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Lista las métricas que informan los scripts de análisis (RF16)")
    public List<MetricaRespuesta> listar() {
        return servicio.listar().stream().map(MetricaRespuesta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra una métrica numérica o de lista (RF16)")
    public MetricaRespuesta crear(@Valid @RequestBody MetricaPeticion peticion,
                                  @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return MetricaRespuesta.de(servicio.crear(new DatosMetrica(peticion.codigo(), peticion.nombre(), peticion.unidad(),
                peticion.tipoDato(), peticion.esLista()), AlcanceUsuario.de(solicitante).actor()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica una métrica; su código no cambia y su tipo tampoco si ya tiene umbrales o mediciones")
    public MetricaRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody ActualizarMetricaPeticion peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return MetricaRespuesta.de(servicio.actualizar(id, new DatosMetrica(null, peticion.nombre(), peticion.unidad(),
                peticion.tipoDato(), peticion.esLista()), AlcanceUsuario.de(solicitante).actor()));
    }

    @PostMapping("/{id}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa una métrica")
    public MetricaRespuesta activar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return MetricaRespuesta.de(servicio.cambiarEstado(id, true, AlcanceUsuario.de(solicitante).actor()));
    }

    @PostMapping("/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva una métrica: sus umbrales dejan de evaluarse")
    public MetricaRespuesta desactivar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return MetricaRespuesta.de(servicio.cambiarEstado(id, false, AlcanceUsuario.de(solicitante).actor()));
    }
}
