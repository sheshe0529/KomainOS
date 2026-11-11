package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.PuertoCuentasServicio.ResumenCuenta;
import com.komainos.mantenimiento.model.ActividadEjecucion;
import com.komainos.mantenimiento.model.EstadoActividad;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.FaseActividad;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.NivelActividad;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.ActividadEjecucionRepositorio;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.mantenimiento.repository.OrdenDetalleRepositorio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Registro de actividad de las ejecuciones remotas (DEC-59): cada conexión con un servidor, la comprobación de acceso,
 * el inicio y el fin de los análisis y de las tareas, con la hora exacta y el motivo de cada fallo. Lo leen «Ver
 * actividad» de la orden y el detalle del historial de ejecución, también mientras la ejecución avanza.
 * <p>
 * Cada evento se guarda en su propia transacción, para que se vea al instante y no se pierda si la operación que lo
 * originó se deshace. Registrar nunca interrumpe una ejecución: un error al guardar solo queda en el log del backend.
 */
@Service
@Slf4j
public class ServicioActividad {

    private static final Map<TipoEvaluacion, String> OPERACION = Map.of(
            TipoEvaluacion.PREVIA, "Evaluación previa",
            TipoEvaluacion.POSTERIOR, "Análisis posterior",
            TipoEvaluacion.REVISION_PERIODICA, "Revisión periódica");
    private static final Map<NivelAlerta, String> NIVEL = Map.of(
            NivelAlerta.NORMAL, "Normal", NivelAlerta.ADVERTENCIA, "Advertencia", NivelAlerta.CRITICO, "Crítico");

    private final ActividadEjecucionRepositorio repositorio;
    private final EvaluacionRepositorio evaluaciones;
    private final OrdenDetalleRepositorio detalles;
    private final MopRepositorio mops;
    private final PuertoCuentasServicio cuentas;
    private final PropiedadesAutomatizacion propiedades;
    private final Clock reloj;
    private final TransactionTemplate propia;
    private final TransactionTemplate lectura;

    public ServicioActividad(ActividadEjecucionRepositorio repositorio, EvaluacionRepositorio evaluaciones,
                             OrdenDetalleRepositorio detalles, MopRepositorio mops, PuertoCuentasServicio cuentas,
                             PropiedadesAutomatizacion propiedades, Clock reloj,
                             PlatformTransactionManager transacciones) {
        this.repositorio = repositorio;
        this.evaluaciones = evaluaciones;
        this.detalles = detalles;
        this.mops = mops;
        this.cuentas = cuentas;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.propia = new TransactionTemplate(transacciones);
        this.propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lectura.setReadOnly(true);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Análisis: evaluación previa, análisis posterior y revisión periódica (EjecutorAnalisis)
    // ---------------------------------------------------------------------------------------------------------------

    /** Antes de conectarse: qué análisis empieza y con qué cuenta y transporte se conecta */
    public Analisis inicioAnalisis(Integer idEvaluacion, SolicitudEjecucion solicitud) {
        Analisis a = seguro(() -> lectura.execute(estado -> evaluaciones.findConDetalleById(idEvaluacion)
                .map(e -> new Analisis(e.getId(), e.getServidor().getId(),
                        e.getDetalle() == null ? null : e.getDetalle().getId(), e.getTipo(), version(e)))
                .orElse(null)));
        if (a == null) {
            return null;
        }
        registrar(a.evento(ahora(), NivelActividad.INFO, null, operacion(a.tipo(), "iniciada"), null, null));
        if (solicitud != null) {
            registrar(a.evento(ahora(), NivelActividad.INFO, null, "Conexión iniciada", conexion(solicitud),
                    null));
        }
        return a;
    }

    /** Con el resultado ya registrado en la evaluación: el acceso, si llegó a ejecutarse, y cómo terminó */
    public void finAnalisis(Analisis a) {
        if (a == null) {
            return;
        }
        Final f = seguro(() -> lectura.execute(estado -> evaluaciones.findConDetalleById(a.idEvaluacion())
                .map(e -> {
                    ServicioEvaluaciones.cargar(e);
                    return new Final(e.getEstado().name(), e.getError(), e.estaFinalizada() ? e.nivel() : null,
                            e.getMediciones().size(), duracion(e.getFechaInicio(), e.getFechaFin()));
                })
                .orElse(null)));
        if (f == null) {
            return;
        }
        switch (f.estado()) {
            case "FINALIZADA" -> {
                registrar(a.evento(ahora(), NivelActividad.INFO, EstadoActividad.COMPLETADO, "Acceso verificado",
                        "La conexión, el inicio de sesión y la elevación configurada funcionaron: el script de análisis "
                                + "respondió.", null));
                registrar(a.evento(ahora(), NivelActividad.INFO, EstadoActividad.COMPLETADO,
                        operacion(a.tipo(), "completada"),
                        "Resultado del análisis: %s · %d %s · %s".formatted(NIVEL.get(f.nivel()), f.mediciones(),
                                f.mediciones() == 1 ? "métrica" : "métricas", texto(f.duracion())), null));
            }
            case "FALLIDA" -> {
                if (llegoAEjecutarse(f.error())) {
                    registrar(a.evento(ahora(), NivelActividad.INFO, EstadoActividad.COMPLETADO, "Acceso verificado",
                            "La conexión y el inicio de sesión funcionaron: el script llegó a ejecutarse.", null));
                }
                registrar(a.evento(ahora(), NivelActividad.ERROR, EstadoActividad.FALLIDO,
                        operacion(a.tipo(), "ha fallado"),
                        "Duración: " + texto(f.duracion()), f.error()));
            }
            default -> {
                // Sigue en curso: lo cierra la recuperación al arrancar
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ejecución de la MOP en un servidor (ServicioOrquestador)
    // ---------------------------------------------------------------------------------------------------------------

    public void inicioEjecucion(Integer idDetalle, SolicitudEjecucion acceso, int tareas) {
        Integer servidor = acceso.idServidor();
        registrar(evento(servidor, idDetalle, null, NivelActividad.INFO, null, "Ejecución iniciada",
                tareas == 1 ? "1 tarea en la MOP" : tareas + " tareas en la MOP", null, null));
        registrar(evento(servidor, idDetalle, null, NivelActividad.INFO, null, "Comprobación de acceso iniciada",
                conexion(acceso), null, null));
    }

    /** El script de la prueba informa con qué usuario y privilegios se ejecutarán las tareas (DEC-57) */
    public void finComprobacion(Integer idDetalle, SolicitudEjecucion acceso, ResultadoEjecucion resultado) {
        Integer servidor = acceso.idServidor();
        if (!resultado.correcto()) {
            registrar(evento(servidor, idDetalle, null, NivelActividad.ERROR, EstadoActividad.FALLIDO,
                    "Comprobación de acceso fallida", null, resultado.error(), null));
            return;
        }
        ServicioConectividad.Permisos permisos = ServicioConectividad.permisos(resultado.salida());
        boolean sinPrivilegios = Boolean.FALSE.equals(permisos.administrador());
        registrar(evento(servidor, idDetalle, null, sinPrivilegios ? NivelActividad.WARNING : NivelActividad.INFO,
                EstadoActividad.COMPLETADO,
                sinPrivilegios ? "Acceso verificado sin privilegios de administrador" : "Acceso verificado",
                permisos.mensaje().isBlank() ? null : permisos.mensaje(), null, null));
    }

    public void inicioTarea(Integer idDetalle, Tarea t) {
        registrar(evento(t.solicitud().idServidor(), idDetalle, t.idTarea(), NivelActividad.INFO, null,
                "Tarea «%s» iniciada".formatted(t.nombre()),
                "Paso %d de %d · %s".formatted(t.paso(), t.total(), conexion(t.solicitud())), null, t.script()));
    }

    public void finTarea(Integer idDetalle, Tarea t, ResultadoEjecucion resultado, Duration duracion) {
        String detalle = resultado.codigoRetorno() == null ? "Duración: " + texto(duracion)
                : "Código de salida %d · %s".formatted(resultado.codigoRetorno(), texto(duracion));
        if (resultado.correcto()) {
            registrar(evento(t.solicitud().idServidor(), idDetalle, t.idTarea(), NivelActividad.INFO,
                    EstadoActividad.COMPLETADO, "Tarea «%s» completada".formatted(t.nombre()), detalle, null,
                    t.script()));
        } else {
            registrar(evento(t.solicitud().idServidor(), idDetalle, t.idTarea(), NivelActividad.ERROR,
                    EstadoActividad.FALLIDO, "Tarea «%s» ha fallado".formatted(t.nombre()), detalle, resultado.error(),
                    t.script()));
        }
    }

    /** Resumen del servidor cuando termina, con las tareas que quedaron sin iniciar */
    public void finEjecucion(Integer idDetalle) {
        Resumen r = seguro(() -> lectura.execute(estado -> {
            OrdenDetalle d = detalles.findById(idDetalle).orElse(null);
            Mop mop = mops.findByDetalleId(idDetalle).orElse(null);
            if (d == null || mop == null) {
                return null;
            }
            long total = mop.getTareas().size();
            long completadas = mop.getTareas().stream().filter(t -> t.getEstado() == EstadoTareaMop.FINALIZADA).count();
            long sinIniciar = mop.getTareas().stream().filter(t -> t.getEstado() == EstadoTareaMop.NO_INICIADA).count();
            return new Resumen(d.getServidor().getId(), d.getEstado(), total, completadas, sinIniciar);
        }));
        if (r == null) {
            return;
        }
        String detalle = "%d de %d %s completadas".formatted(r.completadas(), r.total(),
                r.total() == 1 ? "tarea" : "tareas")
                + (r.sinIniciar() == 0 ? "" : " · %d sin iniciar".formatted(r.sinIniciar()));
        if (r.estado() == EstadoDetalleOrden.FINALIZADO) {
            registrar(evento(r.idServidor(), idDetalle, null, NivelActividad.INFO, EstadoActividad.COMPLETADO,
                    "Ejecución completada", detalle, null, null));
        } else if (r.estado() == EstadoDetalleOrden.FALLIDO) {
            registrar(evento(r.idServidor(), idDetalle, null, NivelActividad.ERROR, EstadoActividad.FALLIDO,
                    "Ejecución fallida", detalle, null, null));
        }
    }

    /** Un error del propio backend o un reinicio que dejó la ejecución a medias */
    public void ejecucionInterrumpida(Integer idServidor, Integer idDetalle, Integer idTarea, String motivo) {
        registrar(evento(idServidor, idDetalle, idTarea, NivelActividad.ERROR, EstadoActividad.FALLIDO,
                idTarea == null ? "Ejecución interrumpida" : "Tarea interrumpida", null, motivo, null));
    }

    public void analisisInterrumpido(Evaluacion e, String motivo) {
        registrar(ActividadEjecucion.de(e.getServidor().getId(), e.getDetalle() == null ? null : e.getDetalle().getId(),
                e.getId(), null, fase(e.getTipo()), NivelActividad.ERROR, EstadoActividad.FALLIDO,
                operacion(e.getTipo(), "interrumpida"), null, motivo, version(e), ahora()));
    }

    /** «Evaluación previa completada», pero «Análisis posterior completado»: el participio concuerda con el análisis */
    static String operacion(TipoEvaluacion tipo, String participio) {
        String concordado = tipo == TipoEvaluacion.POSTERIOR && participio.endsWith("a")
                ? participio.substring(0, participio.length() - 1) + "o" : participio;
        return OPERACION.get(tipo) + " " + concordado;
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** «SERVLNXV01 (172.31.37.104) por SSH (puerto 22) · cuenta «X»: usuario u con llave SSH, elevación sudo» */
    String conexion(SolicitudEjecucion s) {
        PropiedadesAutomatizacion.Ansible config = propiedades.ansible();
        String transporte = s.familia() == FamiliaSistemaOperativo.WINDOWS
                ? "WinRM (%s, puerto %d)".formatted(config.winrm().esquema().toUpperCase(), config.winrm().puerto())
                : "SSH (puerto %d)".formatted(config.ssh().puerto());
        String base = "%s (%s) por %s".formatted(s.hostname(), s.direccionIp(), transporte);
        if (s.idCuentaServicio() == null) {
            return base + " · sin cuenta de servicio";
        }
        Optional<ResumenCuenta> cuenta = seguro(() -> cuentas.resumen(s.idCuentaServicio()));
        if (cuenta == null || cuenta.isEmpty()) {
            return base;
        }
        ResumenCuenta c = cuenta.get();
        // «con contraseña», «con llave SSH»: solo la inicial en minúscula
        String acceso = c.autenticacion() == null || c.autenticacion().isEmpty() ? ""
                : " con " + Character.toLowerCase(c.autenticacion().charAt(0)) + c.autenticacion().substring(1);
        String elevacion = "Ninguno".equals(c.elevacion()) ? "sin elevación"
                : "elevación " + c.elevacion() + ("sudo".equals(c.elevacion())
                ? (c.conContrasenaElevacion() ? " con contraseña" : " sin contraseña") : "");
        return "%s · cuenta «%s»: usuario %s%s, %s".formatted(base, c.nombre(), c.usuario(), acceso, elevacion);
    }

    /** La salida no válida, un código de error o el tiempo excedido ocurren después de conectarse */
    static boolean llegoAEjecutarse(String error) {
        if (error == null) {
            return false;
        }
        return Arrays.stream(new TipoFallo[]{TipoFallo.CONTRATO, TipoFallo.EJECUCION, TipoFallo.TIEMPO_EXCEDIDO})
                .anyMatch(t -> error.startsWith(t.etiqueta()));
    }

    static FaseActividad fase(TipoEvaluacion tipo) {
        return tipo == TipoEvaluacion.POSTERIOR ? FaseActividad.VALIDACION : FaseActividad.EVALUACION;
    }

    static String version(Evaluacion e) {
        return e.getVersion() == null ? null
                : "%s v%d".formatted(e.getVersion().getImplementacion().getScript().getNombre(),
                e.getVersion().getNumeroVersion());
    }

    static String texto(Duration d) {
        if (d == null) {
            return "sin duración registrada";
        }
        long segundos = Math.max(0, d.toSeconds());
        return segundos < 60 ? segundos + " s" : "%d min %d s".formatted(segundos / 60, segundos % 60);
    }

    private static Duration duracion(Instant inicio, Instant fin) {
        return inicio == null || fin == null ? null : Duration.between(inicio, fin);
    }

    private ActividadEjecucion evento(Integer idServidor, Integer idDetalle, Integer idTarea, NivelActividad nivel,
                                      EstadoActividad estado, String accion, String detalle, String error,
                                      String script) {
        return ActividadEjecucion.de(idServidor, idDetalle, null, idTarea, FaseActividad.EJECUCION, nivel, estado,
                accion, detalle, error, script, ahora());
    }

    private void registrar(ActividadEjecucion evento) {
        seguro(() -> propia.execute(estado -> repositorio.save(evento)));
    }

    private <T> T seguro(java.util.function.Supplier<T> paso) {
        try {
            return paso.get();
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la actividad de una ejecución: {}", ex.toString());
            return null;
        }
    }

    private Instant ahora() {
        // Milisegundos: varios pasos ocurren en el mismo segundo y la columna es timestamp(3)
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Un análisis en curso: lo que hace falta para registrar sus eventos sin volver a leerlo */
    public record Analisis(Integer idEvaluacion, Integer idServidor, Integer idDetalle, TipoEvaluacion tipo,
                           String script) {

        ActividadEjecucion evento(Instant ahora, NivelActividad nivel, EstadoActividad estado, String accion,
                                  String detalle, String error) {
            return ActividadEjecucion.de(idServidor, idDetalle, idEvaluacion, null, fase(tipo), nivel, estado, accion,
                    detalle, error, script, ahora);
        }
    }

    /** Una tarea de la MOP a punto de ejecutarse */
    public record Tarea(Integer idTarea, String nombre, String script, int paso, int total,
                        SolicitudEjecucion solicitud) {
    }

    private record Final(String estado, String error, NivelAlerta nivel, int mediciones, Duration duracion) {
    }

    private record Resumen(Integer idServidor, EstadoDetalleOrden estado, long total, long completadas,
                           long sinIniciar) {
    }
}
