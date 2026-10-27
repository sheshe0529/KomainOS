package com.komainos.catalogo.controller;

import com.komainos.catalogo.dto.ImplementacionPeticion;
import com.komainos.catalogo.dto.RevisarSalidaPeticion;
import com.komainos.catalogo.dto.RevisionSalidaRespuesta;
import com.komainos.catalogo.dto.ScriptPeticion;
import com.komainos.catalogo.dto.ScriptPreparadoRespuesta;
import com.komainos.catalogo.dto.ScriptResumenRespuesta;
import com.komainos.catalogo.dto.ScriptRespuesta;
import com.komainos.catalogo.dto.VersionScriptPeticion;
import com.komainos.catalogo.dto.VistaPreviaPeticion;
import com.komainos.catalogo.mapper.CatalogoMapeador;
import com.komainos.catalogo.model.Script;
import com.komainos.catalogo.model.TipoScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosVersion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.FiltroScripts;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.VistaPrevia;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import com.komainos.shared.model.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogo de scripts de análisis y mantenimiento con sus implementaciones y versiones (RF24-RF26, HU17) */
@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
@Tag(name = "Scripts")
public class ScriptController {

    private final ServicioCatalogoAutomatizacion servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Lista los scripts con la compatibilidad y la versión vigente de cada implementación")
    public PaginaRespuesta<ScriptResumenRespuesta> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) TipoScript tipo,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pagina) {
        return PaginaRespuesta.de(servicio.scripts(new FiltroScripts(texto, tipo, activo), pagina),
                CatalogoMapeador::resumen);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Script con sus implementaciones y el código de todas sus versiones")
    public ScriptRespuesta obtener(@PathVariable Integer id) {
        return detalle(servicio.script(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra un script de análisis o de mantenimiento")
    public ScriptRespuesta registrar(@Valid @RequestBody ScriptPeticion p, @AuthenticationPrincipal UsuarioAutenticado u) {
        return detalle(servicio.registrarScript(datos(p), actor(u)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Edita nombre y descripción, o activa y desactiva el script; el tipo no cambia")
    public ScriptRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody ScriptPeticion p,
                                      @AuthenticationPrincipal UsuarioAutenticado u) {
        return detalle(servicio.actualizarScript(id, datos(p), actor(u)));
    }

    @PostMapping("/{id}/implementaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Agrega la implementación para una versión de sistema operativo con su primera versión")
    public ScriptRespuesta implementar(@PathVariable Integer id, @Valid @RequestBody ImplementacionPeticion p,
                                       @AuthenticationPrincipal UsuarioAutenticado u) {
        servicio.implementar(id, p.idVersionSistemaOperativo(),
                new DatosVersion(p.contenido(), p.notasCambio(), CatalogoMapeador.parametros(p.parametros())), actor(u));
        return detalle(servicio.script(id));
    }

    @PostMapping("/implementaciones/{id}/versiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Publica una versión nueva; las anteriores y las fijadas en MOP no cambian")
    public ScriptRespuesta.VersionRespuesta publicar(
            @PathVariable Integer id, @Valid @RequestBody VersionScriptPeticion p,
            @AuthenticationPrincipal UsuarioAutenticado u) {
        return CatalogoMapeador.version(servicio.publicar(id,
                new DatosVersion(p.contenido(), p.notasCambio(), CatalogoMapeador.parametros(p.parametros())), actor(u)));
    }

    @PutMapping("/versiones/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Habilita o deshabilita una versión; deshabilitar la vigente vuelve a la anterior habilitada")
    public ScriptRespuesta.VersionRespuesta cambiarEstado(
            @PathVariable Integer id, @RequestBody EstadoVersionPeticion p,
            @AuthenticationPrincipal UsuarioAutenticado u) {
        return CatalogoMapeador.version(servicio.habilitar(id, p.habilitada(), actor(u)));
    }

    @GetMapping("/versiones/{id}/vista-previa")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Archivo que se ejecuta con esta versión: el código con sus parámetros como variables")
    public ScriptPreparadoRespuesta vistaPreviaVersion(@PathVariable Integer id) {
        return CatalogoMapeador.preparado(servicio.vistaPrevia(id), List.of());
    }

    @PostMapping("/vista-previa")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Prepara un borrador del editor igual que en la ejecución, sin guardarlo")
    public ScriptPreparadoRespuesta vistaPrevia(@Valid @RequestBody VistaPreviaPeticion p) {
        VistaPrevia vista = servicio.vistaPrevia(p.familia(), p.contenido(), CatalogoMapeador.parametros(p.parametros()));
        return CatalogoMapeador.preparado(vista.script(), vista.problemas());
    }

    @PostMapping("/revisar-salida")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Comprueba la salida de un análisis contra el contrato JSON y el catálogo de métricas")
    public RevisionSalidaRespuesta revisarSalida(@Valid @RequestBody RevisarSalidaPeticion p) {
        return CatalogoMapeador.revision(servicio.revisarSalida(p.salida()));
    }

    public record EstadoVersionPeticion(boolean habilitada) {
    }

    private ScriptRespuesta detalle(Script script) {
        return CatalogoMapeador.script(script, servicio.tareasActivasDe(script.getId()));
    }

    private static DatosScript datos(ScriptPeticion p) {
        return new DatosScript(p.nombre(), p.tipo(), p.descripcion(), p.activo());
    }

    private static Actor actor(UsuarioAutenticado u) {
        return Actor.usuario(u.id());
    }
}
