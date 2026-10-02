package com.komainos.credencial.controller;

import com.komainos.credencial.dto.AsignacionServidoresPeticion;
import com.komainos.credencial.dto.CredencialPeticion;
import com.komainos.credencial.dto.CuentaPredeterminadaPeticion;
import com.komainos.credencial.dto.CuentaServicioRespuesta;
import com.komainos.credencial.dto.ResultadoAsignacionRespuesta;
import com.komainos.credencial.dto.ServidorAsignableRespuesta;
import com.komainos.credencial.mapper.CredencialMapeador;
import com.komainos.credencial.service.ServicioCredenciales;
import com.komainos.inventario.service.ServicioAsignacionCuentas;
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

/** Edición, nuevo secreto, revocación y revelado usan los endpoints comunes de /api/credenciales */
@RestController
@RequestMapping("/api/cuentas-servicio")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Cuentas de servicio")
public class CuentaServicioController {

    private final ServicioCredenciales servicio;
    private final ServicioAsignacionCuentas asignaciones;

    @GetMapping
    @Operation(summary = "Lista las cuentas de servicio con su uso, sin sus secretos (RF05, RF06)")
    public List<CuentaServicioRespuesta> listar() {
        return servicio.cuentasDeServicio().stream().map(CredencialMapeador::cuenta).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una cuenta de servicio (RF04, RF05, HU04)")
    public CuentaServicioRespuesta registrar(@Valid @RequestBody CredencialPeticion peticion,
                                             @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        Integer id = servicio.registrarCuenta(peticion.aDatos(), AlcanceUsuario.de(solicitante).actor()).getId();
        return servicio.cuentasDeServicio().stream().filter(c -> c.cuenta().getId().equals(id))
                .findFirst().map(CredencialMapeador::cuenta).orElseThrow();
    }

    @GetMapping("/asignaciones")
    @Operation(summary = "Servidores con configuración de mantenimiento y la cuenta que tienen asignada (RF07)")
    public List<ServidorAsignableRespuesta> asignaciones() {
        return asignaciones.configuracionesDeServidores().stream().map(CredencialMapeador::asignable).toList();
    }

    @PutMapping("/{id}/servidores")
    @Operation(summary = "Asigna la cuenta a los servidores seleccionados (RF07)")
    public ResultadoAsignacionRespuesta asignar(@PathVariable Integer id,
                                                @Valid @RequestBody AsignacionServidoresPeticion peticion,
                                                @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        int asignados = asignaciones.asignarAServidores(id, peticion.idsServidores(),
                AlcanceUsuario.de(solicitante).actor());
        return new ResultadoAsignacionRespuesta(asignados,
                "Cuenta asignada a %d servidor(es). Aplica a las órdenes nuevas".formatted(asignados));
    }

    @PutMapping("/predeterminada")
    @Operation(summary = "Define la cuenta que usan las configuraciones sin cuenta propia (RF06)")
    public List<CuentaServicioRespuesta> definirPredeterminada(@Valid @RequestBody CuentaPredeterminadaPeticion peticion,
                                                               @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        asignaciones.definirPredeterminada(peticion.idCuentaServicio(), AlcanceUsuario.de(solicitante).actor());
        return listar();
    }
}
