package com.komainos.inventario.controller;

import com.komainos.inventario.dto.ActualizarExcepcionUmbralPeticion;
import com.komainos.inventario.dto.ExcepcionUmbralPeticion;
import com.komainos.inventario.dto.ExcepcionUmbralRespuesta;
import com.komainos.inventario.dto.PruebaUmbralRespuesta;
import com.komainos.inventario.dto.UmbralPeticion;
import com.komainos.inventario.dto.UmbralRespuesta;
import com.komainos.inventario.dto.UmbralServidorRespuesta;
import com.komainos.inventario.mapper.UmbralMapeador;
import com.komainos.inventario.service.ServicioUmbrales;
import com.komainos.inventario.service.ServicioUmbrales.DatosExcepcion;
import com.komainos.inventario.service.ServicioUmbrales.DatosUmbral;
import com.komainos.inventario.service.ServicioUmbralesServidor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Umbrales con excepciones por servidor, grupo, entorno o criticidad, y lo que rige para cada servidor (RF16, HU12) */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Umbrales")
public class UmbralController {

    private final ServicioUmbrales servicio;
    private final ServicioUmbralesServidor porServidor;

    @GetMapping("/umbrales")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Lista los umbrales con su valor global, opcionalmente de una métrica")
    public List<UmbralRespuesta> listar(@RequestParam(required = false) Integer idMetrica) {
        return servicio.listar(idMetrica).stream().map(UmbralMapeador::resumen).toList();
    }

    @GetMapping("/umbrales/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Detalle de un umbral con sus excepciones por orden de prioridad")
    public UmbralRespuesta obtener(@PathVariable Integer id) {
        return UmbralMapeador.detalle(servicio.obtener(id));
    }

    @PostMapping("/umbrales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra un umbral numérico o de lista según la métrica (RF16)")
    public UmbralRespuesta crear(@Valid @RequestBody UmbralPeticion peticion,
                                 @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.detalle(servicio.crear(datos(peticion), actor(solicitante)));
    }

    @PutMapping("/umbrales/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica el valor global de un umbral; el operador no cambia si tiene excepciones")
    public UmbralRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody UmbralPeticion peticion,
                                      @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.detalle(servicio.actualizar(id, datos(peticion), actor(solicitante)));
    }

    @PostMapping("/umbrales/{id}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa un umbral")
    public UmbralRespuesta activar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.detalle(servicio.cambiarEstado(id, true, actor(solicitante)));
    }

    @PostMapping("/umbrales/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva un umbral: deja de evaluarse junto con sus excepciones")
    public UmbralRespuesta desactivar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.detalle(servicio.cambiarEstado(id, false, actor(solicitante)));
    }

    @DeleteMapping("/umbrales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Elimina un umbral sin evaluaciones, con sus excepciones")
    public void eliminar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.eliminar(id, actor(solicitante));
    }

    @PostMapping("/umbrales/{id}/excepciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra valores propios del umbral para un servidor, un grupo, un entorno o una criticidad")
    public ExcepcionUmbralRespuesta crearExcepcion(@PathVariable Integer id,
                                                   @Valid @RequestBody ExcepcionUmbralPeticion peticion,
                                                   @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.excepcion(servicio.crearExcepcion(id, new DatosExcepcion(peticion.alcance(),
                peticion.idObjetivo(), peticion.valorAdvertencia(), peticion.valorCritico(), peticion.idListaReferencia(),
                peticion.motivo()), actor(solicitante)));
    }

    @PutMapping("/umbrales/{id}/excepciones/{idExcepcion}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica los valores o la lista de una excepción; su alcance no cambia")
    public ExcepcionUmbralRespuesta actualizarExcepcion(@PathVariable Integer id, @PathVariable Integer idExcepcion,
                                                        @Valid @RequestBody ActualizarExcepcionUmbralPeticion peticion,
                                                        @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.excepcion(servicio.actualizarExcepcion(id, idExcepcion, new DatosExcepcion(null, null,
                peticion.valorAdvertencia(), peticion.valorCritico(), peticion.idListaReferencia(), peticion.motivo()),
                actor(solicitante)));
    }

    @PostMapping("/umbrales/{id}/excepciones/{idExcepcion}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa una excepción")
    public ExcepcionUmbralRespuesta activarExcepcion(@PathVariable Integer id, @PathVariable Integer idExcepcion,
                                                     @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.excepcion(servicio.cambiarEstadoExcepcion(id, idExcepcion, true, actor(solicitante)));
    }

    @PostMapping("/umbrales/{id}/excepciones/{idExcepcion}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva una excepción: se aplica la siguiente en prioridad")
    public ExcepcionUmbralRespuesta desactivarExcepcion(@PathVariable Integer id, @PathVariable Integer idExcepcion,
                                                        @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.excepcion(servicio.cambiarEstadoExcepcion(id, idExcepcion, false, actor(solicitante)));
    }

    @DeleteMapping("/umbrales/{id}/excepciones/{idExcepcion}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Elimina una excepción que no se aplicó en evaluaciones")
    public void eliminarExcepcion(@PathVariable Integer id, @PathVariable Integer idExcepcion,
                                  @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.eliminarExcepcion(id, idExcepcion, actor(solicitante));
    }

    /** Cualquier rol; el responsable solo sobre sus servidores (404 si es ajeno) */
    @GetMapping("/servidores/{id}/umbrales")
    @Operation(summary = "Umbrales que rigen para el servidor, de dónde salen y su valor global")
    public List<UmbralServidorRespuesta> delServidor(@PathVariable Integer id,
                                                     @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return porServidor.aplicables(id, AlcanceUsuario.de(solicitante)).stream()
                .map(UmbralMapeador::delServidor).toList();
    }

    @GetMapping("/servidores/{id}/umbrales/{idUmbral}/prueba")
    @Operation(summary = "Evalúa un valor o una lista de elementos con lo que rige para el servidor, sin registrarlo")
    public PruebaUmbralRespuesta probar(@PathVariable Integer id, @PathVariable Integer idUmbral,
                                        @RequestParam(required = false) BigDecimal valor,
                                        @RequestParam(required = false) String elementos,
                                        @RequestParam(required = false) Integer idGrupo,
                                        @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UmbralMapeador.prueba(porServidor.probar(id, idUmbral, valor, elementos, idGrupo,
                AlcanceUsuario.de(solicitante)));
    }

    private static DatosUmbral datos(UmbralPeticion p) {
        return new DatosUmbral(p.idMetrica(), p.nombre(), p.operador(), p.valorAdvertencia(), p.valorCritico(),
                p.idListaReferencia(), p.nivelFaltante(), p.nivelSobrante());
    }

    private static Actor actor(UsuarioAutenticado solicitante) {
        return AlcanceUsuario.de(solicitante).actor();
    }
}
