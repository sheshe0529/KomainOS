package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.event.CicloMantenimientoCerrado;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.MotivoCierre;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Cierra el ciclo con su motivo (RF63, DEC-61), completa una baja pendiente (RF72) y avisa a la planificación para
 * generar la siguiente orden automática (RF29), que toma la periodicidad vigente de la configuración.
 */
@Service
@RequiredArgsConstructor
public class ServicioCierreMantenimiento {

    private final UsuarioRepositorio usuarios;
    private final ServicioServidor servidores;
    private final ServicioAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @Transactional
    public void cerrar(Orden orden, MotivoCierre motivo, String observaciones, Actor actor) {
        if (orden.getEstado() == EstadoOrden.CERRADA) {
            return;
        }
        Instant ahora = Tiempo.ahora(reloj);
        Usuario autor = actor.esSistema() ? null : usuarios.findById(actor.usuarioId()).orElse(null);
        orden.cerrar(motivo, observaciones, autor, ahora);
        auditoria.registrar(actor, Operacion.de("CERRAR_MANTENIMIENTO", "orden", orden.getId())
                .sobreOrden(orden.getId())
                .motivo("%s: %s".formatted(motivo.etiqueta(), orden.getObservacionesCierre())));
        orden.getDetalles().forEach(d -> servidores.aplicarBajaPendiente(d.getServidor().getId()));
        eventos.publishEvent(new CicloMantenimientoCerrado(orden.getId(), actor));
    }
}
