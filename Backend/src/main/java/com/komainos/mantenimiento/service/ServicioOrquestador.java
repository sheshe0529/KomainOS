package com.komainos.mantenimiento.service;

import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.service.EjecutorRemoto;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.ParametrosSistema;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenIncidencia;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.PrioridadOrden;
import com.komainos.mantenimiento.repository.ConfirmacionFueraVentanaRepositorio;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.mantenimiento.repository.MopTareaRepositorio;
import com.komainos.mantenimiento.repository.OrdenDetalleRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.Paralelo;
import com.komainos.shared.util.Tiempo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orquestador de ejecución (RF51, RF52, RF58-RF60, EA03, EA07). La reserva de un servidor se confirma en la base antes
 * de abrir la conexión, así dos procesos nunca intervienen el mismo servidor a la vez. Antes de la primera tarea se
 * comprueban las condiciones de inicio con una prueba de acceso (CP132); las tareas corren en orden de prioridad, una a
 * la vez y fuera de cualquier transacción. Ante un fallo, el fin de la ventana o la duración máxima por MOP, lo que está
 * en curso termina y lo pendiente no se inicia.
 */
@Service
@Slf4j
public class ServicioOrquestador {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("America/Lima"));
    private static final int MAXIMO_POR_VUELTA = 500;

    private final OrdenRepositorio ordenes;
    private final OrdenDetalleRepositorio detalles;
    private final MopRepositorio mops;
    private final MopTareaRepositorio tareas;
    private final ConfirmacionFueraVentanaRepositorio excepciones;
    private final ServicioParametrosSistema parametros;
    private final ServicioIncidencias incidencias;
    private final ServicioValidaciones validaciones;
    private final EjecutorRemoto remoto;
    private final BloqueoAutomatizacion bloqueo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final ExecutorService trabajadores = Executors.newVirtualThreadPerTaskExecutor();

    public ServicioOrquestador(OrdenRepositorio ordenes, OrdenDetalleRepositorio detalles, MopRepositorio mops,
                               MopTareaRepositorio tareas, ConfirmacionFueraVentanaRepositorio excepciones,
                               ServicioParametrosSistema parametros, ServicioIncidencias incidencias,
                               ServicioValidaciones validaciones, EjecutorRemoto remoto, BloqueoAutomatizacion bloqueo,
                               Clock reloj, PlatformTransactionManager transacciones) {
        this.ordenes = ordenes;
        this.detalles = detalles;
        this.mops = mops;
        this.tareas = tareas;
        this.excepciones = excepciones;
        this.parametros = parametros;
        this.incidencias = incidencias;
        this.validaciones = validaciones;
        this.remoto = remoto;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Reserva lo que puede iniciar ahora y lo ejecuta en segundo plano; devuelve cuántos servidores iniciaron */
    public int despachar() {
        int cantidad = 0;
        Integer idDetalle;
        while (cantidad < MAXIMO_POR_VUELTA && (idDetalle = transaccion.execute(estado -> reservar())) != null) {
            Integer reservado = idDetalle;
            trabajadores.submit(() -> ejecutarDetalle(reservado));
            cantidad++;
        }
        return cantidad;
    }

    /**
     * Ejecuta la cola hasta que no quede nada que pueda iniciar y espera a que termine: los servidores que se habilitan
     * después del piloto o del anterior de un grupo secuencial también corren. Lo usan las pruebas y el recorrido manual.
     */
    public int drenar() {
        int total = 0;
        for (int vuelta = 0; vuelta < MAXIMO_POR_VUELTA; vuelta++) {
            List<Integer> lote = new ArrayList<>();
            Integer idDetalle;
            while (lote.size() < MAXIMO_POR_VUELTA && (idDetalle = transaccion.execute(estado -> reservar())) != null) {
                lote.add(idDetalle);
            }
            if (lote.isEmpty()) {
                return total;
            }
            total += lote.size();
            Paralelo.paraCada(lote, this::ejecutarDetalle)
                    .forEach(ex -> log.error("Error inesperado al ejecutar un servidor de la cola", ex));
        }
        return total;
    }

    /**
     * RF51, RF68: un servidor a la vez y el límite global de ejecuciones, del que el lanzamiento inmediato está exento.
     * La orden entra en cola al llegar su fecha (CP105) y nunca antes.
     */
    private Integer reservar() {
        bloqueo.tomar();
        Instant ahora = Tiempo.ahora(reloj);
        ParametrosSistema p = parametros.obtener();
        List<OrdenDetalle> activos = detalles.findByEstado(EstadoDetalleOrden.EN_EJECUCION);
        Set<Integer> ocupados = new HashSet<>();
        activos.forEach(d -> ocupados.add(d.getServidor().getId()));
        long normales = activos.stream().filter(d -> d.getOrden().getOrigen() != OrigenOrden.LANZAMIENTO_INMEDIATO).count();

        List<Orden> candidatas = new ArrayList<>(ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(
                EnumSet.of(EstadoOrden.AUTORIZADA, EstadoOrden.EN_COLA, EstadoOrden.EN_EJECUCION)));
        candidatas.sort(Comparator.comparingInt((Orden o) -> o.prioridad() == PrioridadOrden.ALTA ? 0 : 1)
                .thenComparingInt(o -> o.getNivelCriticidad().getPrioridad())
                .thenComparing(Orden::getFechaInicioProgramada)
                .thenComparing(Orden::getId));

        for (Orden orden : candidatas) {
            if (ahora.isBefore(orden.getFechaInicioProgramada())) {
                continue;
            }
            if (orden.getEstado() == EstadoOrden.AUTORIZADA) {
                orden.cambiarEstado(EstadoOrden.EN_COLA, null, "Se alcanzó la fecha autorizada", ahora);
                orden.getDetalles().stream()
                        .filter(d -> d.getEstado() == EstadoDetalleOrden.PENDIENTE)
                        .forEach(d -> d.cambiarEstado(EstadoDetalleOrden.EN_COLA, ahora));
            }
            boolean inmediata = orden.getOrigen() == OrigenOrden.LANZAMIENTO_INMEDIATO;
            if (!inmediata && normales >= p.getMaxEjecucionesConcurrentes()) {
                continue;
            }
            Optional<OrdenDetalle> reservado = reservarEn(orden, ocupados, ahora);
            if (reservado.isPresent()) {
                return reservado.get().getId();
            }
        }
        return null;
    }

    private Optional<OrdenDetalle> reservarEn(Orden orden, Set<Integer> ocupados, Instant ahora) {
        boolean secuencial = orden.esGrupal() && orden.getModoEjecucionAplicado() == ModoEjecucion.SECUENCIAL;
        if (secuencial && orden.getDetalles().stream().anyMatch(d -> d.getEstado() == EstadoDetalleOrden.EN_EJECUCION)) {
            return Optional.empty();
        }
        Optional<OrdenDetalle> piloto = orden.getDetalles().stream().filter(OrdenDetalle::isEsServidorPiloto).findFirst();
        for (OrdenDetalle detalle : orden.getDetalles()) {
            if (detalle.getEstado() != EstadoDetalleOrden.EN_COLA) {
                continue;
            }
            // RF58: los demás servidores esperan a que el piloto termine sin fallos
            if (piloto.isPresent() && !piloto.get().getId().equals(detalle.getId())
                    && piloto.get().getEstado() != EstadoDetalleOrden.FINALIZADO) {
                continue;
            }
            if (ocupados.contains(detalle.getServidor().getId())) {
                // En un grupo secuencial el siguiente espera su turno: no se adelanta al servidor ocupado
                if (secuencial) {
                    return Optional.empty();
                }
                continue;
            }
            if (detalle.getServidor().getEstado() != EstadoServidor.ACTIVO) {
                detalle.omitirSiPendiente();
                continue;
            }
            if (!excepciones.existsByOrdenId(orden.getId()) && !ahora.isBefore(orden.getFinVentanaAplicada())) {
                // EA07: la ventana terminó antes de poder iniciar; no se abre ninguna conexión
                iniciarOrden(orden, "Comprobación de las condiciones de inicio", ahora);
                incidencias.registrar(detalle, OrigenIncidencia.TIEMPO_EXCEDIDO,
                        "No se inició la intervención en %s: la ventana permisiva terminó el %s"
                                .formatted(detalle.getServidor().getHostname(), HORA.format(orden.getFinVentanaAplicada())));
                return Optional.empty();
            }
            iniciarOrden(orden, "Capacidad y exclusión por servidor verificadas", ahora);
            detalle.cambiarEstado(EstadoDetalleOrden.EN_EJECUCION, ahora);
            return Optional.of(detalle);
        }
        if (orden.getEstado() == EstadoOrden.EN_COLA && orden.getDetalles().stream().allMatch(OrdenDetalle::estaTerminado)) {
            iniciarOrden(orden, "Comprobación de las condiciones de inicio", ahora);
            incidencias.registrar(orden.getDetalles().getFirst(), OrigenIncidencia.FALLO_EJECUCION,
                    "Ningún servidor de la orden estaba activo para iniciar el mantenimiento");
        }
        return Optional.empty();
    }

    private static void iniciarOrden(Orden orden, String motivo, Instant ahora) {
        if (orden.getEstado() == EstadoOrden.EN_COLA) {
            orden.cambiarEstado(EstadoOrden.EN_EJECUCION, null, motivo, ahora);
        }
    }

    private void ejecutarDetalle(Integer idDetalle) {
        Integer idOrden = null;
        try {
            Inicio inicio = transaccion.execute(estado -> prepararInicio(idDetalle));
            idOrden = inicio.idOrden();
            ResultadoEjecucion acceso = remoto.probarConexion(inicio.conectividad());
            if (!acceso.correcto()) {
                transaccion.executeWithoutResult(estado -> fallarInicio(idDetalle, acceso));
            } else {
                Trabajo trabajo;
                while ((trabajo = transaccion.execute(estado -> siguiente(idDetalle))) != null) {
                    Trabajo actual = trabajo;
                    ResultadoEjecucion resultado = remoto.ejecutar(actual.solicitud());
                    transaccion.executeWithoutResult(estado -> registrar(actual, resultado));
                    if (!resultado.correcto()) {
                        break;
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.error("No se pudo completar la ejecución del detalle {}", idDetalle, ex);
            transaccion.executeWithoutResult(estado -> fallarPorError(idDetalle, ex));
        } finally {
            Integer orden = idOrden != null ? idOrden
                    : transaccion.execute(estado -> detalles.findById(idDetalle).map(d -> d.getOrden().getId()).orElse(null));
            if (orden != null) {
                validaciones.completarEjecucion(orden);
            }
        }
    }

    /** RF52: la cuenta fijada en la MOP es la que se prueba y la que ejecuta */
    private Inicio prepararInicio(Integer idDetalle) {
        OrdenDetalle detalle = detalles.findConOrdenById(idDetalle).orElseThrow();
        Mop mop = mops.findByDetalleId(idDetalle).orElseThrow();
        Servidor s = detalle.getServidor();
        Integer cuenta = mop.getTareas().stream().map(MopTarea::getIdCuentaServicio).filter(id -> id != null)
                .findFirst().orElse(detalle.getOrden().getIdCuentaServicio());
        return new Inicio(detalle.getOrden().getId(), SolicitudEjecucion.conectividad(s.getId(), s.getHostname(),
                s.getDireccionIp(), s.familia(), cuenta, 2));
    }

    /** CP132: sin acceso no se inicia ninguna tarea y la prueba fallida queda como incidencia */
    private void fallarInicio(Integer idDetalle, ResultadoEjecucion acceso) {
        bloqueo.tomar();
        OrdenDetalle detalle = detalles.findConOrdenById(idDetalle).orElseThrow();
        if (detalle.getEstado() != EstadoDetalleOrden.EN_EJECUCION) {
            return;
        }
        mops.findByDetalleId(idDetalle).ifPresent(Mop::omitirPendientes);
        detalle.cambiarEstado(EstadoDetalleOrden.FALLIDO, Tiempo.ahora(reloj));
        incidencias.registrar(detalle, OrigenIncidencia.PRUEBA_PILOTO,
                "No se cumplieron las condiciones de inicio en %s. %s".formatted(detalle.getServidor().getHostname(),
                        acceso.error()));
    }

    /** La siguiente tarea pendiente, o null si el servidor terminó, falló o debe detenerse (RF59, RF60) */
    private Trabajo siguiente(Integer idDetalle) {
        bloqueo.tomar();
        OrdenDetalle detalle = detalles.findConOrdenById(idDetalle).orElseThrow();
        Orden orden = detalle.getOrden();
        Mop mop = mops.findByDetalleId(idDetalle).orElseThrow();
        Instant ahora = Tiempo.ahora(reloj);
        if (detalle.getEstado() != EstadoDetalleOrden.EN_EJECUCION) {
            return null;
        }
        if (orden.getEstado() != EstadoOrden.EN_EJECUCION) {
            // Otro servidor falló: lo pendiente de este no se inicia
            mop.omitirPendientes();
            detalle.cambiarEstado(EstadoDetalleOrden.FALLIDO, ahora);
            return null;
        }
        Optional<MopTarea> siguiente = mop.siguientePendiente();
        if (siguiente.isEmpty()) {
            detalle.cambiarEstado(EstadoDetalleOrden.FINALIZADO, ahora);
            return null;
        }
        int maximoMop = parametros.obtener().getMaxDuracionMopMinutos();
        boolean finVentana = !excepciones.existsByOrdenId(orden.getId()) && !ahora.isBefore(orden.getFinVentanaAplicada());
        boolean excedida = Duration.between(detalle.getFechaRealInicio(), ahora).compareTo(Duration.ofMinutes(maximoMop)) >= 0;
        if (finVentana || excedida) {
            mop.omitirPendientes();
            detalle.cambiarEstado(EstadoDetalleOrden.FALLIDO, ahora);
            incidencias.registrar(detalle, OrigenIncidencia.TIEMPO_EXCEDIDO, finVentana
                    ? "Se alcanzó el fin de la ventana permisiva (%s) en %s: no se iniciaron las tareas pendientes"
                    .formatted(HORA.format(orden.getFinVentanaAplicada()), detalle.getServidor().getHostname())
                    : "Se superó la duración máxima por MOP (%d min) en %s: no se iniciaron las tareas pendientes"
                    .formatted(maximoMop, detalle.getServidor().getHostname()));
            return null;
        }
        MopTarea tarea = siguiente.get();
        tarea.iniciar(ahora);
        VersionScript version = tarea.getVersion();
        if (version == null) {
            throw new ReglaNegocioException("La tarea «%s» no tiene una versión fijada".formatted(tarea.getTarea().getNombre()));
        }
        Servidor s = detalle.getServidor();
        SolicitudEjecucion solicitud = new SolicitudEjecucion(s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(),
                tarea.getIdCuentaServicio(), PropositoEjecucion.TAREA, version.getContenido(),
                ServicioCatalogoAutomatizacion.valoresDeEjecucion(version), parametros.obtener().getMaxDuracionTareaMinutos(),
                List.of());
        return new Trabajo(tarea.getId(), solicitud);
    }

    private void registrar(Trabajo trabajo, ResultadoEjecucion resultado) {
        bloqueo.tomar();
        MopTarea tarea = tareas.findById(trabajo.idTarea()).orElseThrow();
        tarea.terminar(resultado.correcto(), resultado.codigoRetorno(), resultado.salida(), resultado.error(),
                Tiempo.ahora(reloj));
        if (resultado.correcto()) {
            return;
        }
        OrdenDetalle detalle = tarea.getMop().getDetalle();
        detalle.cambiarEstado(EstadoDetalleOrden.FALLIDO, Tiempo.ahora(reloj));
        incidencias.registrar(detalle, detalle.isEsServidorPiloto() ? OrigenIncidencia.PRUEBA_PILOTO : OrigenIncidencia.FALLO_EJECUCION,
                "La tarea «%s» falló en %s. %s".formatted(tarea.getTarea().getNombre(), detalle.getServidor().getHostname(),
                        resultado.error()));
    }

    /** Un error del propio sistema no deja el servidor ocupado: se registra como fallo de la ejecución */
    private void fallarPorError(Integer idDetalle, RuntimeException ex) {
        bloqueo.tomar();
        OrdenDetalle detalle = detalles.findConOrdenById(idDetalle).orElse(null);
        if (detalle == null || detalle.getEstado() != EstadoDetalleOrden.EN_EJECUCION) {
            return;
        }
        Instant ahora = Tiempo.ahora(reloj);
        mops.findByDetalleId(idDetalle).ifPresent(m -> {
            m.getTareas().forEach(t -> t.interrumpir("La ejecución se detuvo por un error del sistema", ahora));
            m.omitirPendientes();
        });
        detalle.cambiarEstado(EstadoDetalleOrden.FALLIDO, ahora);
        incidencias.registrar(detalle, OrigenIncidencia.FALLO_EJECUCION,
                "No se pudo completar la ejecución en %s: %s".formatted(detalle.getServidor().getHostname(),
                        ex instanceof ReglaNegocioException ? ex.getMessage() : "error interno; revise el registro del backend"));
    }

    @PreDestroy
    void detener() {
        trabajadores.shutdown();
    }

    private record Inicio(Integer idOrden, SolicitudEjecucion conectividad) {
    }

    private record Trabajo(Integer idTarea, SolicitudEjecucion solicitud) {
    }
}
