package com.komainos.mantenimiento.controller;

import com.komainos.inventario.model.FinalidadCuenta;

import com.komainos.catalogo.mapper.CatalogoMapeador;
import com.komainos.mantenimiento.dto.AnalisisGrupoRespuesta;
import com.komainos.mantenimiento.dto.CicloPeticiones;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta;
import com.komainos.mantenimiento.dto.OperacionRespuestas;
import com.komainos.mantenimiento.mapper.AutomatizacionMapeador;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.service.ServicioAnalisisGrupo;
import com.komainos.mantenimiento.service.ServicioConectividad;
import com.komainos.mantenimiento.service.ServicioConectividad.ResultadoConexion;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion;
import com.komainos.mantenimiento.service.ServicioEvaluaciones;
import com.komainos.mantenimiento.service.ServicioPreparacionAutomatizacion;
import com.komainos.mantenimiento.service.ServicioPruebaAnalisis;
import com.komainos.mantenimiento.service.ServicioRevisionPeriodica;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que se hace sobre un servidor fuera de una orden: verificar el acceso de la cuenta (RF71), revisarlo ahora (RF37),
 * consultar sus análisis y su preparación, y probar una versión de análisis antes de usarla en el ciclo. También los
 * análisis de un grupo, agrupados por evento.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Análisis de servidores")
public class AnalisisServidorController {

    private final ServicioConectividad conectividad;
    private final ServicioRevisionPeriodica revision;
    private final ServicioEvaluaciones evaluaciones;
    private final ServicioPreparacionAutomatizacion preparacion;
    private final ServicioPruebaAnalisis pruebas;
    private final ServicioConsultaAutomatizacion consulta;
    private final ServicioAnalisisGrupo analisisGrupo;

    @PostMapping("/servidores/{id}/conectividad")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Verifica conectividad, autenticación y permisos con la cuenta del servidor o una candidata")
    public OperacionRespuestas.ConexionRespuesta probarServidor(@PathVariable Integer id,
                                                                @RequestParam(required = false) Integer idCuenta,
                                                                @RequestParam(defaultValue = "MANTENIMIENTO") FinalidadCuenta finalidad,
                                                                @AuthenticationPrincipal UsuarioAutenticado u) {
        return conexion(conectividad.probarServidor(id, idCuenta, finalidad, AlcanceUsuario.de(u).actor()));
    }

    @PostMapping("/grupos/{id}/conectividad")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Verifica el acceso de la cuenta del grupo, o de una candidata, en cada integrante (CP021)")
    public List<OperacionRespuestas.ConexionRespuesta> probarGrupo(@PathVariable Integer id,
                                                                   @RequestParam(required = false) Integer idCuenta,
                                                                @RequestParam(defaultValue = "MANTENIMIENTO") FinalidadCuenta finalidad,
                                                                   @AuthenticationPrincipal UsuarioAutenticado u) {
        return conectividad.probarGrupo(id, idCuenta, finalidad, AlcanceUsuario.de(u).actor()).stream()
                .map(AnalisisServidorController::conexion).toList();
    }

    @PostMapping("/servidores/{id}/acceso")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Prueba en un paso el acceso y los permisos de las cuentas de evaluación y de mantenimiento "
            + "del servidor (una vez si son la misma), o de una candidata (DEC-59)")
    public List<OperacionRespuestas.ConexionRespuesta> probarAccesoServidor(@PathVariable Integer id,
                                                                            @RequestParam(required = false) Integer idCuenta,
                                                                            @AuthenticationPrincipal UsuarioAutenticado u) {
        return conectividad.probarAccesoServidor(id, idCuenta, AlcanceUsuario.de(u).actor()).stream()
                .map(AnalisisServidorController::conexion).toList();
    }

    @PostMapping("/grupos/{id}/acceso")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Lo mismo en cada integrante del grupo (CP021, DEC-59)")
    public List<OperacionRespuestas.ConexionRespuesta> probarAccesoGrupo(@PathVariable Integer id,
                                                                         @RequestParam(required = false) Integer idCuenta,
                                                                         @AuthenticationPrincipal UsuarioAutenticado u) {
        return conectividad.probarAccesoGrupo(id, idCuenta, AlcanceUsuario.de(u).actor()).stream()
                .map(AnalisisServidorController::conexion).toList();
    }

    @PostMapping("/servidores/{id}/revision")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Ejecuta ahora la revisión periódica del servidor (RF37)")
    public FichaAutomatizacionRespuesta.AnalisisRespuesta revisar(@PathVariable Integer id,
                                                                  @AuthenticationPrincipal UsuarioAutenticado u) {
        return analisis(revision.revisar(id, AlcanceUsuario.de(u).actor()));
    }

    @GetMapping("/servidores/{id}/evaluaciones")
    @Operation(summary = "Análisis del servidor, del más reciente al más antiguo")
    public PaginaRespuesta<FichaAutomatizacionRespuesta.AnalisisRespuesta> historial(
            @PathVariable Integer id,
            @PageableDefault(size = 20) Pageable pagina,
            @AuthenticationPrincipal UsuarioAutenticado u) {
        return PaginaRespuesta.de(evaluaciones.historial(id, AlcanceUsuario.de(u), pagina), this::analisis);
    }

    @GetMapping("/grupos/{id}/evaluaciones")
    @Operation(summary = "Análisis del grupo por evento: revisiones periódicas, evaluaciones previas y validaciones técnicas")
    public PaginaRespuesta<AnalisisGrupoRespuesta> historialGrupo(
            @PathVariable Integer id,
            @PageableDefault(size = 20) Pageable pagina,
            @AuthenticationPrincipal UsuarioAutenticado u) {
        return PaginaRespuesta.de(analisisGrupo.historial(id, AlcanceUsuario.de(u), pagina), a -> {
            // Los integrantes suelen compartir la cuenta: cada nombre se busca una sola vez
            Map<Integer, String> cuentas = new HashMap<>();
            a.evaluaciones().stream().map(Evaluacion::getIdCuentaServicio).distinct()
                    .forEach(c -> consulta.nombreCuenta(c).ifPresent(n -> cuentas.put(c, n)));
            return new AnalisisGrupoRespuesta(a.tipo(), a.orden() == null ? null : a.orden().getId(),
                    a.orden() == null ? null : a.orden().getCodigo(), a.fecha(),
                    a.evaluaciones().stream().map(e -> AutomatizacionMapeador.analisis(e, cuentas)).toList());
        });
    }

    @GetMapping("/servidores/{id}/preparacion-automatizacion")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Qué le falta al servidor para que el ciclo automático pueda analizarlo y ejecutar")
    public ServicioPreparacionAutomatizacion.Preparacion preparacion(@PathVariable Integer id,
                                                                     @AuthenticationPrincipal UsuarioAutenticado u) {
        return preparacion.consultar(id, AlcanceUsuario.de(u));
    }

    @PostMapping("/scripts/versiones/{id}/prueba")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Ejecuta una versión de análisis en un servidor y revisa su salida, sin registrar una evaluación")
    public OperacionRespuestas.PruebaAnalisisRespuesta probarAnalisis(
            @PathVariable Integer id,
            @Valid @RequestBody CicloPeticiones.ProbarAnalisisPeticion p,
            @AuthenticationPrincipal UsuarioAutenticado u) {
        ServicioPruebaAnalisis.Prueba r = pruebas.probar(id, p.idServidor(), AlcanceUsuario.de(u).actor());
        return new OperacionRespuestas.PruebaAnalisisRespuesta(r.correcto(), r.fallo(), r.error(), r.salida(),
                r.revision() == null ? null : CatalogoMapeador.revision(r.revision()), r.advertencia());
    }

    @GetMapping("/scripts/versiones/{id}/ejecuciones")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Tareas de MOP fijadas o ejecutadas con esta versión (RF26)")
    public PaginaRespuesta<OperacionRespuestas.UsoVersionRespuesta> usos(@PathVariable Integer id,
                                                                         @PageableDefault(size = 20) Pageable pagina) {
        return PaginaRespuesta.de(consulta.usosVersion(id, pagina), AutomatizacionMapeador::uso);
    }

    private FichaAutomatizacionRespuesta.AnalisisRespuesta analisis(Evaluacion e) {
        Map<Integer, String> cuentas = new HashMap<>();
        consulta.nombreCuenta(e.getIdCuentaServicio()).ifPresent(n -> cuentas.put(e.getIdCuentaServicio(), n));
        return AutomatizacionMapeador.analisis(e, cuentas);
    }

    private static OperacionRespuestas.ConexionRespuesta conexion(ResultadoConexion r) {
        return new OperacionRespuestas.ConexionRespuesta(r.idServidor(), r.servidor(), r.idCuenta(), r.cuenta(),
                r.correcto(), r.fallo(), r.mensaje(), r.administrador(), r.finalidades());
    }
}
