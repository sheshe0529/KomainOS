package com.komainos.inventario.controller;

import com.komainos.inventario.dto.EntornoPeticion;
import com.komainos.inventario.dto.EntornoRespuesta;
import com.komainos.inventario.dto.NivelCriticidadPeticion;
import com.komainos.inventario.dto.NivelCriticidadRespuesta;
import com.komainos.inventario.dto.SistemaOperativoPeticion;
import com.komainos.inventario.dto.SistemaOperativoRespuesta;
import com.komainos.inventario.dto.VersionSistemaOperativoPeticion;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.model.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Catálogos del inventario")
public class CatalogoController {

    private final ServicioCatalogos servicio;

    @GetMapping("/entornos")
    @Operation(summary = "Lista los entornos")
    public List<EntornoRespuesta> listarEntornos() {
        return servicio.listarEntornos().stream().map(EntornoRespuesta::de).toList();
    }

    @PostMapping("/entornos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea un entorno")
    public EntornoRespuesta crearEntorno(@Valid @RequestBody EntornoPeticion peticion,
                                         @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return EntornoRespuesta.de(servicio.crearEntorno(peticion.nombre(), peticion.descripcion(), actor(solicitante)));
    }

    @PutMapping("/entornos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica un entorno")
    public EntornoRespuesta actualizarEntorno(@PathVariable Integer id, @Valid @RequestBody EntornoPeticion peticion,
                                              @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return EntornoRespuesta.de(servicio.actualizarEntorno(id, peticion.nombre(), peticion.descripcion(),
                actor(solicitante)));
    }

    @PostMapping("/entornos/{id}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa un entorno")
    public EntornoRespuesta activarEntorno(@PathVariable Integer id,
                                           @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return EntornoRespuesta.de(servicio.cambiarEstadoEntorno(id, true, actor(solicitante)));
    }

    @PostMapping("/entornos/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva un entorno conservando sus referencias históricas")
    public EntornoRespuesta desactivarEntorno(@PathVariable Integer id,
                                              @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return EntornoRespuesta.de(servicio.cambiarEstadoEntorno(id, false, actor(solicitante)));
    }

    @GetMapping("/niveles-criticidad")
    @Operation(summary = "Lista los niveles de criticidad, del más crítico al menos crítico")
    public List<NivelCriticidadRespuesta> listarCriticidades() {
        return servicio.listarCriticidades().stream().map(NivelCriticidadRespuesta::de).toList();
    }

    @PostMapping("/niveles-criticidad")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea un nivel de criticidad")
    public NivelCriticidadRespuesta crearCriticidad(@Valid @RequestBody NivelCriticidadPeticion peticion,
                                                    @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return NivelCriticidadRespuesta.de(servicio.crearCriticidad(peticion.aDatos(), actor(solicitante)));
    }

    @PutMapping("/niveles-criticidad/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica un nivel de criticidad sin alterar configuraciones existentes")
    public NivelCriticidadRespuesta actualizarCriticidad(@PathVariable Integer id,
                                                         @Valid @RequestBody NivelCriticidadPeticion peticion,
                                                         @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return NivelCriticidadRespuesta.de(servicio.actualizarCriticidad(id, peticion.aDatos(), actor(solicitante)));
    }

    @PostMapping("/niveles-criticidad/{id}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa un nivel de criticidad")
    public NivelCriticidadRespuesta activarCriticidad(@PathVariable Integer id,
                                                      @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return NivelCriticidadRespuesta.de(servicio.cambiarEstadoCriticidad(id, true, actor(solicitante)));
    }

    @PostMapping("/niveles-criticidad/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva un nivel de criticidad")
    public NivelCriticidadRespuesta desactivarCriticidad(@PathVariable Integer id,
                                                         @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return NivelCriticidadRespuesta.de(servicio.cambiarEstadoCriticidad(id, false, actor(solicitante)));
    }

    @DeleteMapping("/niveles-criticidad/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Elimina un nivel de criticidad que no esté asignado (HU11 CA3)")
    public void eliminarCriticidad(@PathVariable Integer id,
                                   @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.eliminarCriticidad(id, actor(solicitante));
    }

    @GetMapping("/sistemas-operativos")
    @Operation(summary = "Lista los sistemas operativos con sus versiones")
    public List<SistemaOperativoRespuesta> listarSistemasOperativos() {
        return servicio.listarSistemasOperativos().stream().map(SistemaOperativoRespuesta::de).toList();
    }

    @PostMapping("/sistemas-operativos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra un sistema operativo")
    public SistemaOperativoRespuesta crearSistemaOperativo(@Valid @RequestBody SistemaOperativoPeticion peticion,
                                                           @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return SistemaOperativoRespuesta.de(servicio.crearSistemaOperativo(peticion.nombre(), peticion.familia(),
                actor(solicitante)));
    }

    @PutMapping("/sistemas-operativos/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica un sistema operativo")
    public SistemaOperativoRespuesta actualizarSistemaOperativo(@PathVariable Integer id,
                                                                @Valid @RequestBody SistemaOperativoPeticion peticion,
                                                                @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        boolean activo = peticion.activo() == null || peticion.activo();
        return SistemaOperativoRespuesta.de(servicio.actualizarSistemaOperativo(id, peticion.nombre(),
                peticion.familia(), activo, actor(solicitante)));
    }

    @PostMapping("/sistemas-operativos/{id}/versiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Agrega una versión a un sistema operativo")
    public SistemaOperativoRespuesta agregarVersion(@PathVariable Integer id,
                                                    @Valid @RequestBody VersionSistemaOperativoPeticion peticion,
                                                    @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return SistemaOperativoRespuesta.de(servicio.agregarVersion(id, peticion.version(), actor(solicitante)));
    }

    @PutMapping("/versiones-sistema-operativo/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica una versión de sistema operativo")
    public SistemaOperativoRespuesta actualizarVersion(@PathVariable Integer id,
                                                       @Valid @RequestBody VersionSistemaOperativoPeticion peticion,
                                                       @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        boolean activo = peticion.activo() == null || peticion.activo();
        return SistemaOperativoRespuesta.de(servicio.actualizarVersion(id, peticion.version(), activo,
                actor(solicitante)));
    }

    private static Actor actor(UsuarioAutenticado solicitante) {
        return AlcanceUsuario.de(solicitante).actor();
    }
}
