package com.komainos.inventario.controller;

import com.komainos.inventario.dto.BajaPeticion;
import com.komainos.inventario.dto.ConfiguracionPeticion;
import com.komainos.inventario.dto.FichaServidorRespuesta;
import com.komainos.inventario.dto.ResultadoBajaRespuesta;
import com.komainos.inventario.dto.ServidorPeticion;
import com.komainos.inventario.dto.ServidorResumenRespuesta;
import com.komainos.inventario.dto.VentanasPeticion;
import com.komainos.inventario.mapper.InventarioMapeador;
import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.service.ServicioServidor.DatosConfiguracion;
import com.komainos.inventario.service.ServicioServidor.ResultadoBaja;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
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

@RestController
@RequestMapping("/api/servidores")
@RequiredArgsConstructor
@Tag(name = "Inventario de servidores")
public class ServidorController {

    private final ServicioServidor servicio;

    @GetMapping
    @Operation(summary = "Lista el inventario dentro del alcance del usuario (RF11)")
    public PaginaRespuesta<ServidorResumenRespuesta> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoServidor estado,
            @RequestParam(required = false) Integer idEntorno,
            @RequestParam(required = false) Integer idNivelCriticidad,
            @RequestParam(required = false) Integer idSistemaOperativo,
            @RequestParam(required = false) Integer idResponsable,
            @RequestParam(required = false) String vdc,
            @PageableDefault(size = 20, sort = "hostname", direction = Sort.Direction.ASC) Pageable paginacion,
            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        var filtro = new FiltroServidores(texto, estado, idEntorno, idNivelCriticidad, idSistemaOperativo,
                idResponsable, vdc);
        return PaginaRespuesta.de(servicio.listar(filtro, AlcanceUsuario.de(solicitante), paginacion),
                InventarioMapeador::resumen);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta la ficha del servidor (RF14)")
    public FichaServidorRespuesta ficha(@PathVariable Integer id,
                                        @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return InventarioMapeador.ficha(servicio.ficha(id, AlcanceUsuario.de(solicitante)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra un servidor detectando duplicados (RF09)")
    public FichaServidorRespuesta crear(@Valid @RequestBody ServidorPeticion peticion,
                                        @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        Integer id = servicio.crear(peticion.aDatos(), alcance.actor()).getId();
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualiza los atributos del servidor (RF10)")
    public FichaServidorRespuesta actualizar(@PathVariable Integer id, @Valid @RequestBody ServidorPeticion peticion,
                                             @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.actualizar(id, peticion.aDatos(), alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    @PutMapping("/{id}/configuracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea o modifica la configuración de mantenimiento y su cuenta de servicio (RF05, RF17, RF70)")
    public FichaServidorRespuesta configurar(@PathVariable Integer id,
                                             @Valid @RequestBody ConfiguracionPeticion peticion,
                                             @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.configurar(id, new DatosConfiguracion(peticion.frecuenciaRevisionDias(),
                peticion.frecuenciaMantenimientoDias(), peticion.modalidadPlanificacion(),
                peticion.idCuentaServicio()), alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    /** El responsable solo edita la ventana de sus servidores, lo verifica el servicio (RF19) */
    @PutMapping("/{id}/ventanas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'RESPONSABLE')")
    @Operation(summary = "Reemplaza la ventana permisiva del servidor (RF18, RF19)")
    public FichaServidorRespuesta reemplazarVentanas(@PathVariable Integer id,
                                                     @Valid @RequestBody VentanasPeticion peticion,
                                                     @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        var intervalos = peticion.ventanas().stream()
                .map(v -> new IntervaloSemanal(v.diaInicio(), v.horaInicio(), v.diaFin(), v.horaFin()))
                .toList();
        servicio.reemplazarVentanas(id, intervalos, alcance);
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }

    /** La baja es una transición de estado y no un DELETE: se conserva el historial (RF72) */
    @PostMapping("/{id}/baja")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Solicita la baja del servidor conservando su historial (RF72)")
    public ResultadoBajaRespuesta darDeBaja(@PathVariable Integer id, @Valid @RequestBody BajaPeticion peticion,
                                            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        ResultadoBaja resultado = servicio.solicitarBaja(id, peticion.motivo(), alcance.actor());
        String mensaje = resultado.aplicada()
                ? "Servidor dado de baja; se retiraron %d mantenimiento(s) pendiente(s)".formatted(resultado.ordenesRetiradas())
                : "La baja quedó pendiente: se aplicará al finalizar el mantenimiento en curso";
        return new ResultadoBajaRespuesta(resultado.aplicada(), resultado.ordenesRetiradas(), mensaje,
                InventarioMapeador.solicitud(resultado.solicitud()),
                InventarioMapeador.ficha(servicio.ficha(id, alcance)));
    }

    @PostMapping("/{id}/reactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reactiva un servidor dado de baja (RF73)")
    public FichaServidorRespuesta reactivar(@PathVariable Integer id,
                                            @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        AlcanceUsuario alcance = AlcanceUsuario.de(solicitante);
        servicio.reactivar(id, alcance.actor());
        return InventarioMapeador.ficha(servicio.ficha(id, alcance));
    }
}
