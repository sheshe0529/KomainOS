package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.mantenimiento.model.AutorizacionDetalle;
import com.komainos.mantenimiento.model.EstadoAutorizacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.NotificacionRegla;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import com.komainos.mantenimiento.model.SolicitudAutorizacion;
import com.komainos.mantenimiento.model.TipoAprobador;
import com.komainos.mantenimiento.model.TipoDestinatario;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.repository.SolicitudAutorizacionRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Autorización del cambio (RF43-RF50, RF75, EA05). La regla se elige por el entorno y la mayor afectación de las MOP;
 * las aprobaciones se piden en secuencia, responsable y después administrador, con un único plazo compartido: en una
 * orden programada vence al inicio programado y en una inmediata corre desde la primera solicitud.
 */
@Service
@RequiredArgsConstructor
public class ServicioAutorizaciones {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    private final OrdenRepositorio ordenes;
    private final MopRepositorio mops;
    private final SolicitudAutorizacionRepositorio solicitudes;
    private final UsuarioRepositorio usuarios;
    private final ServicioConsultaOrdenes consulta;
    private final ServicioPoliticasCambio politicas;
    private final ServicioNotificaciones notificaciones;
    private final ServicioCierreMantenimiento cierre;
    private final PuertoProgramacionCambio programacion;
    private final BloqueoAutomatizacion bloqueo;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    /** EA05: aplica la política y abre una ronda; sin aprobadores, la orden queda autorizada de inmediato (CP116) */
    @Transactional
    public SolicitudAutorizacion solicitar(Orden orden, Actor actor) {
        Instant ahora = Tiempo.ahora(reloj);
        NivelAfectacion afectacion = mops.findByDetalleOrdenIdOrderByDetallePosicionEjecucionAsc(orden.getId()).stream()
                .map(Mop::afectacion)
                .reduce(NivelAfectacion.SIN_AFECTACION, NivelAfectacion::mayor);
        Integer idEntorno = orden.getDetalles().getFirst().getServidor().getEntorno().getId();
        ReglaPoliticaCambio regla = politicas.aplicable(idEntorno, afectacion);
        int ronda = solicitudes.findFirstByOrdenIdOrderByNumeroRondaDesc(orden.getId())
                .map(s -> s.getNumeroRonda() + 1).orElse(1);
        Instant limite = orden.esInmediata()
                ? ahora.plus(Duration.ofHours(orden.getNivelCriticidad().getPlazoAutorizacionHoras()))
                : orden.getFechaInicioProgramada();
        SolicitudAutorizacion solicitud = solicitudes.save(SolicitudAutorizacion.abrir(orden, regla, ronda, limite, ahora));
        String descripcion = "%s · afectación %s".formatted(regla.getEntorno().getNombre(), afectacion);

        if (solicitud.esAutomatica()) {
            orden.cambiarEstado(EstadoOrden.AUTORIZADA, null,
                    "Autorización automática según la política de cambio (%s)".formatted(descripcion), ahora);
        } else {
            if (orden.getEstado() != EstadoOrden.PENDIENTE_AUTORIZACION) {
                orden.cambiarEstado(EstadoOrden.PENDIENTE_AUTORIZACION, null,
                        "Se requieren aprobaciones según la política de cambio (%s)".formatted(descripcion), ahora);
            }
            avisarAprobador(orden, solicitud.pendienteActual().orElseThrow());
        }
        auditoria.registrar(actor, Operacion.de("APLICAR_POLITICA_CAMBIO", "solicitud_autorizacion", solicitud.getId())
                .sobreOrden(orden.getId())
                .motivo("Ronda %d · %s · %s · plazo %s".formatted(ronda, descripcion,
                        solicitud.esAutomatica() ? "automática" : aprobadores(solicitud), FECHA.format(limite))));
        for (NotificacionRegla n : politicas.notificaciones(regla.getId())) {
            notificaciones.aOrden(orden, TipoAlerta.SOLICITUD_AUTORIZACION, "Política de cambio aplicada en " + orden.getCodigo(),
                    "%s: %s.".formatted(descripcion, solicitud.esAutomatica() ? "autorización automática"
                            : "se requieren aprobaciones de " + aprobadores(solicitud)),
                    notificaciones.porTipo(orden, n.getDestinatario()));
        }
        return solicitud;
    }

    /**
     * RF44, RF47, RF48: solo responde quien tiene la decisión pendiente en la secuencia, dentro del plazo. El rechazo
     * cierra el ciclo sin incidencia (CP152); una fecha propuesta queda sujeta a la decisión del administrador.
     */
    @Transactional
    public void decidir(Integer idOrden, EstadoAutorizacion decision, String motivo, Instant propuesta,
                        AlcanceUsuario alcance) {
        bloqueo.tomar();
        Orden orden = consulta.obtener(idOrden, alcance);
        Instant ahora = Tiempo.ahora(reloj);
        if (orden.getEstado() != EstadoOrden.PENDIENTE_AUTORIZACION) {
            throw new ReglaNegocioException("La orden %s no está pendiente de autorización".formatted(orden.getCodigo()));
        }
        SolicitudAutorizacion solicitud = rondaVigente(orden);
        if (solicitud.vencida(ahora)) {
            throw new ReglaNegocioException("El plazo compartido de autorización venció el " + FECHA.format(solicitud.getFechaLimite()));
        }
        if (solicitud.propuesta().isPresent()) {
            decidirSobrePropuesta(orden, decision, motivo, alcance, ahora);
            return;
        }
        AutorizacionDetalle pendiente = solicitud.pendienteActual()
                .orElseThrow(() -> new ReglaNegocioException("No quedan aprobaciones pendientes en esta ronda"));
        exigirTurno(orden, pendiente.getTipo(), alcance);
        if (decision == EstadoAutorizacion.FECHA_PROPUESTA && propuesta != null) {
            programacion.verificarFecha(idOrden, propuesta);
        }
        Usuario autor = usuario(alcance.actor());
        pendiente.decidir(decision, autor, motivo, propuesta, ahora);
        auditoria.registrar(alcance.actor(), Operacion.de("DECIDIR_AUTORIZACION", "autorizacion_detalle", pendiente.getId())
                .sobreOrden(idOrden)
                .motivo("%s · %s: %s".formatted(pendiente.getTipo(), decision, motivo)));

        switch (decision) {
            case RECHAZADA -> rechazar(orden, "Rechazado por el %s: %s".formatted(nombre(pendiente.getTipo()), motivo),
                    alcance.actor(), ahora);
            case FECHA_PROPUESTA -> notificaciones.aOrden(orden, TipoAlerta.SOLICITUD_AUTORIZACION,
                    "Fecha propuesta para " + orden.getCodigo(),
                    "El responsable propone iniciar el %s: %s".formatted(FECHA.format(propuesta), motivo),
                    notificaciones.administradores());
            default -> {
                if (solicitud.aprobada()) {
                    orden.cambiarEstado(EstadoOrden.AUTORIZADA, autor, "Aprobaciones completas", ahora);
                    notificaciones.aOrden(orden, TipoAlerta.SOLICITUD_AUTORIZACION, orden.getCodigo() + " autorizada",
                            "Se completaron las aprobaciones; la ejecución inicia en la fecha programada.",
                            notificaciones.involucrados(orden));
                } else {
                    AutorizacionDetalle siguiente = solicitud.pendienteActual().orElseThrow();
                    siguiente.solicitar(ahora);
                    avisarAprobador(orden, siguiente);
                }
            }
        }
    }

    /** RF48: el administrador acepta la fecha propuesta; se reprograma y se abre una ronda nueva con su plazo */
    @Transactional
    public void aceptarPropuesta(Integer idOrden, Actor actor) {
        bloqueo.tomar();
        Orden orden = consulta.obtener(idOrden, new AlcanceUsuario(actor.usuarioId(), Rol.ADMINISTRADOR));
        if (orden.getEstado() != EstadoOrden.PENDIENTE_AUTORIZACION) {
            throw new ReglaNegocioException("La orden %s no está pendiente de autorización".formatted(orden.getCodigo()));
        }
        AutorizacionDetalle propuesta = rondaVigente(orden).propuesta()
                .orElseThrow(() -> new ReglaNegocioException("No hay una fecha propuesta por el responsable"));
        programacion.reprogramarEnAutorizacion(idOrden, propuesta.getFechaPropuesta(),
                "Fecha propuesta por el responsable: " + propuesta.getMotivo(), actor);
        solicitar(orden, actor);
    }

    /** RF50: vencido el plazo sin las aprobaciones, se bloquea la ejecución y se cierra el ciclo (EA05) */
    @Transactional
    public int vencer() {
        bloqueo.tomar();
        Instant ahora = Tiempo.ahora(reloj);
        int vencidas = 0;
        for (Orden orden : ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(EnumSet.of(EstadoOrden.PENDIENTE_AUTORIZACION))) {
            SolicitudAutorizacion solicitud = rondaVigente(orden);
            if (!solicitud.vencida(ahora)) {
                continue;
            }
            solicitud.vencer(ahora);
            orden.cambiarEstado(EstadoOrden.AUTORIZACION_VENCIDA, null, "Plazo compartido de autorización vencido", ahora);
            notificaciones.aOrden(orden, TipoAlerta.AUTORIZACION_VENCIDA, "Autorización vencida: " + orden.getCodigo(),
                    "Venció el plazo sin las aprobaciones requeridas; el mantenimiento se cierra sin ejecutarse.",
                    notificaciones.involucrados(orden));
            cierre.cerrar(orden, "Autorización vencida sin las aprobaciones requeridas", Actor.sistema("VENCIMIENTO_AUTORIZACION"));
            vencidas++;
        }
        return vencidas;
    }

    /** Con una fecha propuesta, el administrador solo la acepta o rechaza el mantenimiento */
    private void decidirSobrePropuesta(Orden orden, EstadoAutorizacion decision, String motivo, AlcanceUsuario alcance,
                                       Instant ahora) {
        if (!alcance.esAdministrador() || decision != EstadoAutorizacion.RECHAZADA) {
            throw new ReglaNegocioException("El responsable propuso otra fecha: el administrador debe aceptarla o rechazar "
                    + "el mantenimiento");
        }
        auditoria.registrar(alcance.actor(), Operacion.de("RECHAZAR_FECHA_PROPUESTA", "orden", orden.getId())
                .sobreOrden(orden.getId()).motivo(motivo));
        rechazar(orden, "Fecha propuesta no aceptada por el administrador: " + motivo, alcance.actor(), ahora);
    }

    private void rechazar(Orden orden, String motivo, Actor actor, Instant ahora) {
        orden.cambiarEstado(EstadoOrden.RECHAZADA, usuario(actor), recortar(motivo), ahora);
        notificaciones.aOrden(orden, TipoAlerta.SOLICITUD_AUTORIZACION, orden.getCodigo() + " rechazada", motivo,
                notificaciones.involucrados(orden));
        cierre.cerrar(orden, recortar(motivo), actor);
    }

    /** CP114: el administrador no puede aprobar antes que el responsable, ni un responsable ajeno a los servidores */
    private static void exigirTurno(Orden orden, TipoAprobador tipo, AlcanceUsuario alcance) {
        boolean corresponde = switch (tipo) {
            case ADMINISTRADOR -> alcance.esAdministrador();
            case RESPONSABLE -> alcance.rol() == Rol.RESPONSABLE && orden.getDetalles().stream()
                    .allMatch(d -> d.getServidor().esVisiblePara(alcance.usuarioId()));
        };
        if (!corresponde) {
            throw new ReglaNegocioException("Todavía no corresponde su aprobación: la solicitud espera la decisión del "
                    + nombre(tipo));
        }
    }

    private void avisarAprobador(Orden orden, AutorizacionDetalle pendiente) {
        Set<Integer> destinatarios = pendiente.getTipo() == TipoAprobador.RESPONSABLE
                ? notificaciones.porTipo(orden, TipoDestinatario.RESPONSABLE)
                : notificaciones.porTipo(orden, TipoDestinatario.ADMINISTRADOR);
        notificaciones.aOrden(orden, TipoAlerta.SOLICITUD_AUTORIZACION,
                "Aprobación del %s requerida: %s".formatted(nombre(pendiente.getTipo()), orden.getCodigo()),
                "Revise la MOP, la duración estimada y los servidores antes del %s."
                        .formatted(FECHA.format(pendiente.getSolicitud().getFechaLimite())),
                destinatarios);
    }

    private SolicitudAutorizacion rondaVigente(Orden orden) {
        return solicitudes.findFirstByOrdenIdOrderByNumeroRondaDesc(orden.getId())
                .orElseThrow(() -> new ReglaNegocioException("La orden no tiene una solicitud de autorización"));
    }

    private Usuario usuario(Actor actor) {
        return actor.esSistema() ? null : usuarios.findById(actor.usuarioId()).orElse(null);
    }

    private static String aprobadores(SolicitudAutorizacion s) {
        Set<String> nombres = new LinkedHashSet<>();
        s.getDecisiones().forEach(d -> nombres.add(nombre(d.getTipo())));
        return String.join(" y ", List.copyOf(nombres));
    }

    private static String nombre(TipoAprobador tipo) {
        return tipo == TipoAprobador.RESPONSABLE ? "responsable" : "administrador";
    }

    private static String recortar(String texto) {
        return texto.length() <= 1000 ? texto : texto.substring(0, 999) + "…";
    }
}
