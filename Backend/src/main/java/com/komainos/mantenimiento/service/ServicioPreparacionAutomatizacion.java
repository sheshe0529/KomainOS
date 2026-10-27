package com.komainos.mantenimiento.service;

import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.ejecucion.service.DiagnosticoAutomatizacion;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Qué le falta a un servidor para que el ciclo automático funcione: cuenta, script de análisis, política del entorno,
 * ventana, umbrales y tareas compatibles, y el estado del transporte. Permite saber antes de probar en un servidor real
 * por qué una evaluación quedaría bloqueada, en lugar de descubrirlo cuando falla.
 */
@Service
@RequiredArgsConstructor
public class ServicioPreparacionAutomatizacion {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    private final ServicioServidor servidores;
    private final ConfiguracionServidorRepositorio configuraciones;
    private final ServicioParametrosSistema parametros;
    private final PuertoCuentasServicio cuentas;
    private final ServicioCatalogoAutomatizacion catalogo;
    private final ServicioPoliticasCambio politicas;
    private final UmbralRepositorio umbrales;
    private final EvaluacionRepositorio evaluaciones;
    private final DiagnosticoAutomatizacion diagnostico;

    @Transactional(readOnly = true)
    public Preparacion consultar(Integer idServidor, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        List<Comprobacion> lista = new ArrayList<>();

        lista.add(servidor.getEstado() == EstadoServidor.ACTIVO
                ? Comprobacion.ok("ESTADO", "Servidor activo", null)
                : Comprobacion.error("ESTADO", "Servidor no activo",
                "Está %s: no genera revisiones ni mantenimientos".formatted(servidor.getEstado())));

        Optional<ConfiguracionServidor> config = configuraciones.findByServidorId(idServidor);
        lista.add(config.isPresent()
                ? Comprobacion.ok("CONFIGURACION", "Configuración de mantenimiento",
                "Revisión cada %d días, mantenimiento cada %d días, modalidad %s".formatted(
                        config.get().getFrecuenciaRevisionDias(), config.get().getFrecuenciaMantenimientoDias(),
                        config.get().getModalidadPlanificacion()))
                : Comprobacion.error("CONFIGURACION", "Sin configuración de mantenimiento",
                "Defina frecuencias, modalidad y cuenta en la ficha del servidor"));

        lista.add(cuenta(servidor, config.map(ConfiguracionServidor::getIdCuentaServicio).orElse(null)));
        lista.add(analisis(servidor));
        lista.add(politica(servidor));
        lista.add(servidor.getVentanas().isEmpty()
                ? Comprobacion.advertencia("VENTANA", "Sin ventana permisiva",
                "No se puede programar; solo admite lanzamientos inmediatos con confirmación expresa")
                : Comprobacion.ok("VENTANA", "Ventana permisiva", "%d intervalo(s) semanales".formatted(
                servidor.getVentanas().size())));
        lista.add(umbralesActivos());
        lista.add(tareasCompatibles(servidor));
        lista.add(ultimaRevision(idServidor));
        lista.add(transporte());

        boolean listo = lista.stream().noneMatch(c -> c.estado() == EstadoComprobacion.ERROR);
        return new Preparacion(servidor.getId(), servidor.getHostname(), listo, lista);
    }

    private Comprobacion cuenta(Servidor servidor, Integer propia) {
        Integer cuenta = propia != null ? propia : parametros.obtener().getIdCuentaServicioPredeterminada();
        if (cuenta == null) {
            return Comprobacion.error("CUENTA", "Sin cuenta de servicio",
                    "Asigne una al servidor o defina la cuenta predeterminada (RF05, RF06)");
        }
        String nombre = cuentas.nombre(cuenta).orElse("#" + cuenta);
        try {
            cuentas.exigirAsignable(cuenta, servidor.familia());
            return Comprobacion.ok("CUENTA", "Cuenta de servicio",
                    "%s (%s). Use «Probar acceso» para verificarla".formatted(nombre,
                            propia != null ? "propia" : "predeterminada"));
        } catch (ReglaNegocioException ex) {
            return Comprobacion.error("CUENTA", "Cuenta de servicio no utilizable", ex.getMessage());
        }
    }

    private Comprobacion analisis(Servidor servidor) {
        String so = servidor.getVersionSistemaOperativo().descripcionCompleta();
        Optional<VersionScript> version = catalogo.buscarAnalisis(servidor.getVersionSistemaOperativo().getId());
        return version
                .map(v -> Comprobacion.ok("ANALISIS", "Script de análisis", "«%s» versión %d para %s".formatted(
                        v.getImplementacion().getScript().getNombre(), v.getNumeroVersion(), so)))
                .orElse(Comprobacion.error("ANALISIS", "Sin script de análisis",
                        "Publique un script de análisis habilitado para %s (RF24)".formatted(so)));
    }

    private Comprobacion politica(Servidor servidor) {
        try {
            politicas.exigirCompleta(servidor.getEntorno());
            return Comprobacion.ok("POLITICA", "Política de cambio", "Completa para el entorno " + servidor.getEntorno().getNombre());
        } catch (ReglaNegocioException ex) {
            return Comprobacion.error("POLITICA", "Política de cambio incompleta", ex.getMessage());
        }
    }

    private Comprobacion umbralesActivos() {
        long cantidad = umbrales.findAplicables().size();
        return cantidad == 0
                ? Comprobacion.advertencia("UMBRALES", "Sin umbrales activos",
                "La evaluación nunca activará tareas: registre métricas y umbrales")
                : Comprobacion.ok("UMBRALES", "Umbrales activos",
                "%d umbral(es): el análisis debe informar sus métricas".formatted(cantidad));
    }

    /** RF42: una tarea activable sin implementación para este sistema dejaría la orden sin implementación */
    private Comprobacion tareasCompatibles(Servidor servidor) {
        Integer idVersionSo = servidor.getVersionSistemaOperativo().getId();
        List<String> sinImplementacion = catalogo.activadas(umbrales.findAplicables().stream().map(u -> u.getId()).toList())
                .stream()
                .filter(t -> catalogo.compatible(t.getScript().getId(), idVersionSo).isEmpty())
                .map(Tarea::getNombre)
                .toList();
        return sinImplementacion.isEmpty()
                ? Comprobacion.ok("TAREAS", "Tareas compatibles",
                "Todas las tareas activas tienen una versión para " + servidor.getVersionSistemaOperativo().descripcionCompleta())
                : Comprobacion.advertencia("TAREAS", "Tareas sin implementación para este sistema",
                "%s: si se activan, la orden quedará sin implementación".formatted(String.join(", ", sinImplementacion)));
    }

    private Comprobacion ultimaRevision(Integer idServidor) {
        return evaluaciones.findFirstByServidorIdAndTipoOrderByFechaInicioDesc(idServidor, TipoEvaluacion.REVISION_PERIODICA)
                .map(e -> e.estaFinalizada()
                        ? Comprobacion.ok("REVISION", "Última revisión", "%s · nivel %s".formatted(
                        FECHA.format(e.getFechaInicio()), e.nivel()))
                        : Comprobacion.advertencia("REVISION", "Última revisión no concluyente",
                        "%s · %s".formatted(FECHA.format(e.getFechaInicio()), e.getError())))
                .orElse(Comprobacion.info("REVISION", "Sin revisiones", "Use «Revisar ahora» para la primera"));
    }

    private Comprobacion transporte() {
        DiagnosticoAutomatizacion.Diagnostico d = diagnostico.consultar();
        if (d.simulado()) {
            return Comprobacion.info("TRANSPORTE", "Modo simulado",
                    "No se conecta a los servidores. Para probar en servidores reales active el modo REAL en un "
                            + "controlador Linux con Ansible");
        }
        return d.ansibleDisponible()
                ? Comprobacion.ok("TRANSPORTE", "Ansible disponible", d.versionAnsible())
                : Comprobacion.error("TRANSPORTE", "Ansible no disponible",
                "No se encontró «%s» en el controlador".formatted(d.ejecutable()));
    }

    public enum EstadoComprobacion { OK, INFO, ADVERTENCIA, ERROR }

    public record Comprobacion(String codigo, String titulo, EstadoComprobacion estado, String detalle) {

        static Comprobacion ok(String codigo, String titulo, String detalle) {
            return new Comprobacion(codigo, titulo, EstadoComprobacion.OK, detalle);
        }

        static Comprobacion info(String codigo, String titulo, String detalle) {
            return new Comprobacion(codigo, titulo, EstadoComprobacion.INFO, detalle);
        }

        static Comprobacion advertencia(String codigo, String titulo, String detalle) {
            return new Comprobacion(codigo, titulo, EstadoComprobacion.ADVERTENCIA, detalle);
        }

        static Comprobacion error(String codigo, String titulo, String detalle) {
            return new Comprobacion(codigo, titulo, EstadoComprobacion.ERROR, detalle);
        }
    }

    /** @param listo ninguna comprobación en error: el ciclo puede analizar y ejecutar en este servidor */
    public record Preparacion(Integer idServidor, String servidor, boolean listo, List<Comprobacion> comprobaciones) {
    }
}
