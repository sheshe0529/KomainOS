package com.komainos.seguridad.controller;

import com.komainos.seguridad.dto.ActualizarUsuarioPeticion;
import com.komainos.seguridad.dto.CrearUsuarioPeticion;
import com.komainos.seguridad.dto.UsuarioRespuesta;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.FiltroUsuarios;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.dto.PaginaRespuesta;
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

/**
 * Usuarios y roles (RF03). Solo el administrador gestiona cuentas.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Usuarios")
public class UsuarioController {

    private final ServicioUsuario servicioUsuario;

    @GetMapping
    @Operation(summary = "Lista usuarios con filtros por texto, rol y estado")
    public PaginaRespuesta<UsuarioRespuesta> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Rol rol,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 50, sort = "nombreCompleto", direction = Sort.Direction.ASC) Pageable paginacion) {
        return PaginaRespuesta.de(
                servicioUsuario.listar(new FiltroUsuarios(texto, rol, activo), paginacion),
                UsuarioRespuesta::de);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un usuario")
    public UsuarioRespuesta obtener(@PathVariable Integer id) {
        return UsuarioRespuesta.de(servicioUsuario.obtener(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cuenta de usuario con un rol vigente")
    public UsuarioRespuesta crear(@Valid @RequestBody CrearUsuarioPeticion peticion,
                                  @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UsuarioRespuesta.de(servicioUsuario.crear(peticion.codigo(), peticion.nombreCompleto(),
                peticion.contrasena(), peticion.rol(), AlcanceUsuario.de(solicitante).actor()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita el nombre y el rol de un usuario")
    public UsuarioRespuesta actualizar(@PathVariable Integer id,
                                       @Valid @RequestBody ActualizarUsuarioPeticion peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UsuarioRespuesta.de(servicioUsuario.actualizar(id, peticion.nombreCompleto(), peticion.rol(),
                AlcanceUsuario.de(solicitante).actor()));
    }

    @PostMapping("/{id}/activacion")
    @Operation(summary = "Activa una cuenta desactivada")
    public UsuarioRespuesta activar(@PathVariable Integer id,
                                    @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UsuarioRespuesta.de(servicioUsuario.activar(id, AlcanceUsuario.de(solicitante).actor()));
    }

    @PostMapping("/{id}/desactivacion")
    @Operation(summary = "Desactiva una cuenta conservando su historial")
    public UsuarioRespuesta desactivar(@PathVariable Integer id,
                                       @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return UsuarioRespuesta.de(servicioUsuario.desactivar(id, AlcanceUsuario.de(solicitante).actor()));
    }
}
