package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.alerta.service.ServicioAlertas;
import com.komainos.alerta.service.ServicioAlertas.DatosAlerta;
import com.komainos.inventario.model.Servidor;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.TipoDestinatario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A quién se avisa de cada evento del ciclo (RF69): el responsable solo de sus servidores y los administradores de todo
 * (HU30 CA2). Los mensajes describen el evento sin datos sensibles (HU30 CA4).
 */
@Service
@RequiredArgsConstructor
public class ServicioNotificaciones {

    private final ServicioAlertas alertas;
    private final UsuarioRepositorio usuarios;

    @Transactional
    public void aOrden(Orden orden, TipoAlerta tipo, String titulo, String mensaje, Collection<Integer> destinatarios) {
        alertas.emitir(DatosAlerta.de(tipo, titulo, mensaje)
                .servidor(orden.esGrupal() ? null : orden.getServidor().getId())
                .grupo(orden.esGrupal() ? orden.getGrupo().getId() : null)
                .orden(orden.getId()), destinatarios);
    }

    @Transactional
    public void aServidor(Servidor servidor, TipoAlerta tipo, String titulo, String mensaje) {
        alertas.emitir(DatosAlerta.de(tipo, titulo, mensaje).servidor(servidor.getId()), deServidor(servidor));
    }

    @Transactional
    public void incidencia(OrdenDetalle detalle, Integer idIncidencia, TipoAlerta tipo, String titulo, String mensaje) {
        Orden orden = detalle.getOrden();
        alertas.emitir(DatosAlerta.de(tipo, titulo, mensaje)
                .servidor(detalle.getServidor().getId())
                .grupo(orden.esGrupal() ? orden.getGrupo().getId() : null)
                .orden(orden.getId())
                .incidencia(idIncidencia), deServidor(detalle.getServidor()));
    }

    @Transactional(readOnly = true)
    public Set<Integer> administradores() {
        return conRol(Rol.ADMINISTRADOR);
    }

    /** RF37: el responsable del servidor y los administradores */
    @Transactional(readOnly = true)
    public Set<Integer> deServidor(Servidor servidor) {
        Set<Integer> ids = new LinkedHashSet<>(administradores());
        ids.add(servidor.getResponsable().getId());
        return ids;
    }

    /** Responsables de los servidores de la orden: en un grupo es uno solo (RF20) */
    public Set<Integer> responsables(Orden orden) {
        Set<Integer> ids = new LinkedHashSet<>();
        orden.getDetalles().forEach(d -> ids.add(d.getServidor().getResponsable().getId()));
        return ids;
    }

    @Transactional(readOnly = true)
    public Set<Integer> involucrados(Orden orden) {
        Set<Integer> ids = new LinkedHashSet<>(administradores());
        ids.addAll(responsables(orden));
        return ids;
    }

    /** RF43: destinatarios configurados en la regla de la política de cambio */
    @Transactional(readOnly = true)
    public Set<Integer> porTipo(Orden orden, TipoDestinatario tipo) {
        return switch (tipo) {
            case RESPONSABLE -> responsables(orden);
            case ADMINISTRADOR -> conRol(Rol.ADMINISTRADOR);
            case OPERADOR -> conRol(Rol.OPERADOR);
        };
    }

    private Set<Integer> conRol(Rol rol) {
        Set<Integer> ids = new LinkedHashSet<>();
        usuarios.findByRolAndActivoTrue(rol).stream().map(Usuario::getId).forEach(ids::add);
        return ids;
    }
}
