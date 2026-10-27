package com.komainos.catalogo.controller;

import com.komainos.catalogo.dto.TareaPeticion;
import com.komainos.catalogo.dto.TareaRespuesta;
import com.komainos.catalogo.mapper.CatalogoMapeador;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de tareas de mantenimiento asociadas a umbrales (RF22, RF23, HU16) */
@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
@Tag(name = "Tareas de mantenimiento")
public class TareaController {

    private final ServicioCatalogoAutomatizacion servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Lista las tareas con su script y los sistemas operativos compatibles")
    public PaginaRespuesta<TareaRespuesta> listar(
            @PageableDefault(size = 20, sort = {"prioridad", "nombre"}, direction = Sort.Direction.ASC) Pageable pagina) {
        return PaginaRespuesta.de(servicio.tareas(pagina), CatalogoMapeador::tarea);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Detalle de una tarea")
    public TareaRespuesta obtener(@PathVariable Integer id) {
        return CatalogoMapeador.tarea(servicio.tarea(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra una tarea; la duración no supera el máximo por tarea")
    public TareaRespuesta registrar(@Valid @RequestBody TareaPeticion p, @AuthenticationPrincipal UsuarioAutenticado u) {
        return guardar(null, p, u);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Edita una tarea; las MOP ya generadas conservan los valores con que se fijaron")
    public TareaRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody TareaPeticion p,
                                     @AuthenticationPrincipal UsuarioAutenticado u) {
        return guardar(id, p, u);
    }

    private TareaRespuesta guardar(Integer id, TareaPeticion p, UsuarioAutenticado u) {
        Tarea.DatosTarea datos = new Tarea.DatosTarea(p.nombre(), p.descripcion(), p.nivelAfectacion(), p.prioridad(),
                p.duracionEstimadaMinutos(), p.activo());
        return CatalogoMapeador.tarea(servicio.guardarTarea(id, p.idScript(), datos, p.idsUmbrales(),
                Actor.usuario(u.id())));
    }
}
