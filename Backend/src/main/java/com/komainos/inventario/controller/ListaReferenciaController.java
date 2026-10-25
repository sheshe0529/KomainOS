package com.komainos.inventario.controller;

import com.komainos.inventario.dto.ListaReferenciaPeticion;
import com.komainos.inventario.dto.ListaReferenciaRespuesta;
import com.komainos.inventario.service.ServicioListasReferencia;
import com.komainos.inventario.service.ServicioListasReferencia.DatosLista;
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
@RequestMapping("/api/listas-referencia")
@RequiredArgsConstructor
@Tag(name = "Listas de referencia")
public class ListaReferenciaController {

    private final ServicioListasReferencia servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Lista las listas de referencia con sus elementos vigentes y su uso")
    public List<ListaReferenciaRespuesta> listar() {
        return servicio.listar().stream().map(ListaReferenciaRespuesta::de).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Consulta una lista de referencia")
    public ListaReferenciaRespuesta obtener(@PathVariable Integer id) {
        return ListaReferenciaRespuesta.de(servicio.obtener(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea una lista de referencia con sus elementos")
    public ListaReferenciaRespuesta crear(@Valid @RequestBody ListaReferenciaPeticion peticion,
                                          @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        Integer id = servicio.crear(datos(peticion), actor(solicitante)).getId();
        return ListaReferenciaRespuesta.de(servicio.obtener(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica nombre y elementos; los elementos quitados quedan con su fecha de baja")
    public ListaReferenciaRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody ListaReferenciaPeticion peticion,
                                               @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.actualizar(id, datos(peticion), peticion.versionEsperada(), actor(solicitante));
        return ListaReferenciaRespuesta.de(servicio.obtener(id));
    }

    @PostMapping("/{id}/activacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa una lista de referencia")
    public ListaReferenciaRespuesta activar(@PathVariable Integer id,
                                            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.cambiarEstado(id, true, actor(solicitante));
        return ListaReferenciaRespuesta.de(servicio.obtener(id));
    }

    @PostMapping("/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Desactiva una lista: lo que ya la usa la conserva, pero no se asigna a nada nuevo")
    public ListaReferenciaRespuesta desactivar(@PathVariable Integer id,
                                               @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.cambiarEstado(id, false, actor(solicitante));
        return ListaReferenciaRespuesta.de(servicio.obtener(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Elimina una lista que ningún umbral, excepción ni evaluación usa")
    public void eliminar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        servicio.eliminar(id, actor(solicitante));
    }

    private static DatosLista datos(ListaReferenciaPeticion p) {
        return new DatosLista(p.nombre(), p.tipoDato(), p.ignoraMayusculas(), p.elementos());
    }

    private static Actor actor(UsuarioAutenticado solicitante) {
        return AlcanceUsuario.de(solicitante).actor();
    }
}
