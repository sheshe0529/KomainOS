package com.komainos.inventario.api;

import com.komainos.inventario.api.dto.ConfiguracionGrupoPeticion;
import com.komainos.inventario.api.dto.FichaGrupoRespuesta;
import com.komainos.inventario.api.dto.GrupoPeticion;
import com.komainos.inventario.api.dto.GrupoResumenRespuesta;
import com.komainos.inventario.api.dto.IntegrantesPeticion;
import com.komainos.inventario.dominio.ServicioGrupo;
import com.komainos.inventario.dominio.ServicioGrupo.DatosConfiguracionGrupo;
import com.komainos.seguridad.dominio.AlcanceUsuario;
import com.komainos.seguridad.dominio.UsuarioAutenticado;
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

/** Grupos de mantenimiento (RF20, RF21, RF76, HU15). */
@RestController
@RequestMapping("/api/grupos")
@RequiredArgsConstructor
@Tag(name = "Grupos de mantenimiento")
public class GrupoController {

    private final ServicioGrupo servicio;

    @GetMapping
    @Operation(summary = "Lista los grupos dentro del alcance del usuario")
    public List<GrupoResumenRespuesta> listar(@AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return servicio.listar(AlcanceUsuario.de(solicitante)).stream().map(InventarioMapeador::resumen).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un grupo con su criticidad y ventana calculadas (RF21, RF76)")
    public FichaGrupoRespuesta ficha(@PathVariable Integer id,
                                     @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return InventarioMapeador.ficha(servicio.ficha(id, AlcanceUsuario.de(solicitante)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea un grupo de mantenimiento")
    public FichaGrupoRespuesta crear(@Valid @RequestBody GrupoPeticion peticion,
                                     @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        Integer id = servicio.crear(peticion.nombre(), peticion.descripcion(), alcance.actor()).getId();
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica el nombre y la descripción del grupo")
    public FichaGrupoRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody GrupoPeticion peticion,
                                          @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.actualizar(id, peticion.nombre(), peticion.descripcion(), alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PutMapping("/{id}/integrantes")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Define los integrantes del grupo (mismo responsable, entorno y SO)")
    public FichaGrupoRespuesta reemplazarIntegrantes(@PathVariable Integer id,
                                                     @Valid @RequestBody IntegrantesPeticion peticion,
                                                     @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.reemplazarIntegrantes(id, peticion.idsServidores(), alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PutMapping("/{id}/configuracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea o modifica la configuración de mantenimiento del grupo (RF17)")
    public FichaGrupoRespuesta configurar(@PathVariable Integer id,
                                          @Valid @RequestBody ConfiguracionGrupoPeticion peticion,
                                          @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.configurar(id, new DatosConfiguracionGrupo(peticion.frecuenciaRevisionDias(),
                peticion.frecuenciaMantenimientoDias(), peticion.modalidadPlanificacion(),
                peticion.modoEjecucion()), alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PostMapping("/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva el grupo")
    public FichaGrupoRespuesta desactivar(@PathVariable Integer id,
                                          @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.desactivar(id, alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PostMapping("/{id}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa el grupo")
    public FichaGrupoRespuesta activar(@PathVariable Integer id,
                                       @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.activar(id, alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }
}
