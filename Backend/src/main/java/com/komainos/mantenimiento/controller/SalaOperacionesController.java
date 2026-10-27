package com.komainos.mantenimiento.controller;

import com.komainos.ejecucion.service.DiagnosticoAutomatizacion;
import com.komainos.mantenimiento.dto.CicloPeticiones;
import com.komainos.mantenimiento.dto.OperacionRespuestas;
import com.komainos.mantenimiento.dto.OrdenResumenRespuesta;
import com.komainos.mantenimiento.mapper.AutomatizacionMapeador;
import com.komainos.mantenimiento.mapper.OrdenMapeador;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.EtapaOrden;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.service.ProcesoAutomatizacion;
import com.komainos.mantenimiento.service.PuertoProgramacionCambio;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion.FiltroEjecuciones;
import com.komainos.mantenimiento.service.ServicioDemostracionAutomatizacion;
import com.komainos.mantenimiento.service.ServicioEvaluacionPrevia;
import com.komainos.mantenimiento.service.ServicioGraficosAutomatizacion;
import com.komainos.mantenimiento.service.ServicioIndicadoresMantenimiento;
import com.komainos.mantenimiento.service.ServicioOrquestador;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.shared.dto.PaginaRespuesta;
import com.komainos.shared.dto.ReferenciaSimple;
import com.komainos.shared.model.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Sala de operaciones (RF34, RF35, RF66, RF67), lanzamiento inmediato (RF31) e historial de ejecución (R3.4) */
@RestController
@RequestMapping("/api/automatizacion")
@RequiredArgsConstructor
@Tag(name = "Sala de operaciones")
public class SalaOperacionesController {

    private final ServicioConsultaAutomatizacion consulta;
    private final ServicioIndicadoresMantenimiento kpis;
    private final ServicioGraficosAutomatizacion graficos;
    private final ProcesoAutomatizacion proceso;
    private final ServicioOrquestador orquestador;
    private final ServicioEvaluacionPrevia evaluacion;
    private final PuertoProgramacionCambio programacion;
    private final ServicioDemostracionAutomatizacion demostracion;
    private final DiagnosticoAutomatizacion diagnostico;

    @GetMapping("/modo")
    @Operation(summary = "Modo de ejecución, estado de Ansible en el controlador y configuración del transporte")
    public DiagnosticoAutomatizacion.Diagnostico modo() {
        return diagnostico.consultar();
    }

    @GetMapping("/sala")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Órdenes por etapa del ciclo con filtros de entorno, criticidad y texto")
    public PaginaRespuesta<OrdenResumenRespuesta> sala(@RequestParam(required = false) EtapaOrden etapa,
                                                       @RequestParam(required = false) Integer idEntorno,
                                                       @RequestParam(required = false) Integer idCriticidad,
                                                       @RequestParam(required = false) String texto,
                                                       @PageableDefault(size = 20, sort = "fechaInicioProgramada",
                                                       direction = Sort.Direction.DESC) Pageable pagina) {
        return PaginaRespuesta.de(consulta.sala(etapa, idEntorno, idCriticidad, texto, pagina), OrdenMapeador::resumen);
    }

    @GetMapping("/indicadores")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Cantidad de órdenes en cada etapa del ciclo")
    public Map<EtapaOrden, Long> indicadores() {
        return consulta.indicadores();
    }

    @GetMapping("/cola")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Servidores en cola o en ejecución con su posición (RF35)")
    public PaginaRespuesta<OperacionRespuestas.ColaRespuesta> cola(@PageableDefault(size = 20) Pageable pagina) {
        Page<OrdenDetalle> resultado = consulta.cola(pagina);
        int[] posicion = {(int) pagina.getOffset()};
        return PaginaRespuesta.de(resultado, d -> AutomatizacionMapeador.cola(d, ++posicion[0]));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Indicadores KPI-01 a KPI-05 del periodo (RF66)")
    public ServicioIndicadoresMantenimiento.InformeKpi kpis(@RequestParam(required = false) Instant desde,
                                                            @RequestParam(required = false) Instant hasta) {
        return kpis.consultar(desde, hasta);
    }

    @GetMapping("/graficos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "CPU y memoria de las últimas 24 horas y ejecuciones de los últimos siete días (RF67)")
    public ServicioGraficosAutomatizacion.ResumenGraficos graficos() {
        return graficos.consultar();
    }

    @GetMapping("/ejecuciones")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Historial de ejecución de los scripts de mantenimiento (R3.4)")
    public PaginaRespuesta<OperacionRespuestas.EjecucionRespuesta> ejecuciones(
            @RequestParam(required = false) Integer idServidor,
            @RequestParam(required = false) Integer idScript,
            @RequestParam(required = false) EstadoTareaMop estado,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) String texto,
            @PageableDefault(size = 20) Pageable pagina) {
        return PaginaRespuesta.de(consulta.ejecuciones(new FiltroEjecuciones(idServidor, idScript, estado, desde, hasta,
                texto), pagina), t -> AutomatizacionMapeador.ejecucion(t, this::cuenta));
    }

    @GetMapping("/ejecuciones/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'OPERADOR')")
    @Operation(summary = "Detalle de una ejecución con su salida completa, ya saneada")
    public OperacionRespuestas.EjecucionDetalleRespuesta ejecucion(@PathVariable Integer id) {
        MopTarea t = consulta.ejecucion(id);
        return new OperacionRespuestas.EjecucionDetalleRespuesta(AutomatizacionMapeador.ejecucion(t, this::cuenta), t.getSalida(),
                t.getPrioridadAplicada(), t.getDuracionEstimadaMinutos());
    }

    @PostMapping("/inmediata")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Lanza un mantenimiento inmediato con prioridad alta e inicia su evaluación (RF31)")
    public ReferenciaSimple inmediata(@Valid @RequestBody CicloPeticiones.LanzamientoInmediatoPeticion p,
                                      @AuthenticationPrincipal UsuarioAutenticado u) {
        Actor actor = AlcanceUsuario.de(u).actor();
        evaluacion.validarLanzamiento(p.idServidor(), p.idGrupo());
        Orden orden = programacion.inmediata(p.idServidor(), p.idGrupo(), p.confirmarFueraVentana(), p.motivo(), null, actor);
        evaluacion.iniciar(orden.getId(), actor);
        return new ReferenciaSimple(orden.getId(), orden.getCodigo());
    }

    @PostMapping("/procesar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Ejecuta ahora un ciclo del proceso automático: plazos, cola, evaluaciones y revisiones")
    public OperacionRespuestas.ResumenProcesoRespuesta procesar() {
        ProcesoAutomatizacion.Resumen r = proceso.procesar();
        return new OperacionRespuestas.ResumenProcesoRespuesta(r.ejecutado(), r.evaluadas(), r.revisadas(), r.despachadas(),
                r.vencidas(), r.descripcion());
    }

    @PostMapping("/ejecutar-cola")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Inicia los servidores de la cola que pueden ejecutarse ahora")
    public OperacionRespuestas.DespachoRespuesta ejecutarCola() {
        int despachados = orquestador.despachar();
        return new OperacionRespuestas.DespachoRespuesta(despachados, despachados == 0
                ? "No hay servidores listos: revise las fechas, la capacidad y la exclusión por servidor"
                : "%d servidor(es) iniciado(s)".formatted(despachados));
    }

    @PostMapping("/demostracion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Agrega ejemplos de métricas, scripts, tareas y política sin reemplazar lo existente")
    public Map<String, Integer> demostracion(@AuthenticationPrincipal UsuarioAutenticado u) {
        return demostracion.cargar(AlcanceUsuario.de(u).actor());
    }

    private String cuenta(Integer idCuenta) {
        return consulta.nombreCuenta(idCuenta).orElse(null);
    }
}
