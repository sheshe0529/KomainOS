package com.komainos.auditoria.controller;

import com.komainos.auditoria.dto.AuditoriaRespuesta;
import com.komainos.auditoria.model.FiltroAuditoria;
import com.komainos.auditoria.service.ServicioConsultaAuditoria;
import com.komainos.shared.dto.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** RNF06: el acceso directo a ambos endpoints exige el rol de administrador. */
@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@RequiredArgsConstructor
@Tag(name = "Auditoría")
public class AuditoriaController {
    private final ServicioConsultaAuditoria consulta;

    @GetMapping
    @Operation(summary = "Consulta los cambios del sistema, del más reciente al más antiguo")
    public PaginaRespuesta<AuditoriaRespuesta> listar(@RequestParam(required = false) String texto,
            @RequestParam(required = false) Integer idUsuario, @RequestParam(required = false) Boolean sistema,
            @RequestParam(required = false) String operacion, @RequestParam(required = false) String entidad,
            @RequestParam(required = false) Instant desde, @RequestParam(required = false) Instant hasta,
            @PageableDefault(size = 25) Pageable pagina) {
        return PaginaRespuesta.de(consulta.listar(new FiltroAuditoria(texto, idUsuario, sistema, operacion, entidad,
                desde, hasta), pagina), AuditoriaRespuesta::de);
    }

    @GetMapping("/filtros")
    @Operation(summary = "Usuarios, operaciones y entidades que tienen eventos de auditoría")
    public ServicioConsultaAuditoria.Opciones filtros() {
        return consulta.filtros();
    }
}
