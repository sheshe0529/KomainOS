package com.komainos.inventario.controller;

import com.komainos.inventario.dto.ParametrosSistemaPeticion;
import com.komainos.inventario.dto.ParametrosSistemaRespuesta;
import com.komainos.inventario.service.ServicioAsignacionCuentas;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parametros-sistema")
@RequiredArgsConstructor
@Tag(name = "Parámetros del sistema")
public class ParametrosSistemaController {

    private final ServicioParametrosSistema servicio;
    private final ServicioAsignacionCuentas cuentasServicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Consulta los parámetros de ejecución")
    public ParametrosSistemaRespuesta obtener() {
        return ParametrosSistemaRespuesta.de(servicio.obtener(), cuentasServicio.predeterminada());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualiza los parámetros de ejecución")
    public ParametrosSistemaRespuesta actualizar(@Valid @RequestBody ParametrosSistemaPeticion peticion,
                                                 @AuthenticationPrincipal UsuarioAutenticado solicitante) {
        return ParametrosSistemaRespuesta.de(servicio.actualizar(peticion.aDatos(),
                AlcanceUsuario.de(solicitante).actor()), cuentasServicio.predeterminada());
    }
}
