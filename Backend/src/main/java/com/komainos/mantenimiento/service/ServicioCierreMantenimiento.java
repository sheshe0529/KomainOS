package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.event.CicloMantenimientoCerrado;
import com.komainos.mantenimiento.model.CierreOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.repository.CierreOrdenRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;

/**
 * Registra el cierre y su periodicidad base, completa una baja pendiente (RF72) y avisa a la planificación
 * para generar la siguiente orden automática (RF29).
 */
@Service
@RequiredArgsConstructor
public class ServicioCierreMantenimiento {

    private final OrdenRepositorio ordenes;
    private final CierreOrdenRepositorio cierres;
    private final ConfiguracionServidorRepositorio configuracionesServidor;
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final ServicioServidor servidores;
    private final ServicioAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Transactional
    public void cerrar(Orden orden, String motivo, Actor actor) {
        if (orden.getEstado() == EstadoOrden.CERRADA) {
            return;
        }
        Instant ahora = Tiempo.ahora(reloj);
        orden.getDetalles().forEach(OrdenDetalle::omitirSiPendiente);
        orden.cambiarEstado(EstadoOrden.CERRADA, null, motivo, ahora);
        cierres.save(CierreOrden.nuevo(orden, periodicidad(orden), ahora, motivo));
        auditoria.registrar(actor, Operacion.de("CERRAR_MANTENIMIENTO", "orden", orden.getId())
                .sobreOrden(orden.getId())
                .motivo(motivo));
        orden.getDetalles().forEach(d -> servidores.aplicarBajaPendiente(d.getServidor().getId()));
        eventos.publishEvent(new CicloMantenimientoCerrado(orden.getId(), actor));
    }

    /** DEC-19, EA04: la cancelación del cronograma termina el ciclo en el siguiente procesamiento */
    @Transactional
    public int cerrarCanceladas() {
        int cerradas = 0;
        for (Orden orden : ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(EnumSet.of(EstadoOrden.CANCELADA))) {
            cerrar(orden, "Programación cancelada", Actor.sistema("CIERRE_CANCELACION"));
            cerradas++;
        }
        return cerradas;
    }

    /** RF46: la periodicidad de la configuración del objetivo, o la de la criticidad si no la tiene */
    private int periodicidad(Orden orden) {
        int porCriticidad = orden.getNivelCriticidad().getFrecuenciaMantenimientoDias();
        return orden.esGrupal()
                ? configuracionesGrupo.findByGrupoId(orden.getGrupo().getId())
                .map(c -> c.getFrecuenciaMantenimientoDias()).orElse(porCriticidad)
                : configuracionesServidor.findByServidorId(orden.getServidor().getId())
                .map(c -> c.getFrecuenciaMantenimientoDias()).orElse(porCriticidad);
    }
}
