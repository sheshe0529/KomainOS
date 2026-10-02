package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.service.PuertoMantenimientos;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ServicioMantenimientosServidor implements PuertoMantenimientos {

    private static final Set<EstadoOrden> CANCELABLES = EnumSet.of(
            EstadoOrden.PROGRAMADA, EstadoOrden.AUTORIZADA, EstadoOrden.EN_COLA);

    private final OrdenRepositorio ordenes;
    private final ServicioAuditoria auditoria;

    @Override
    @Transactional(readOnly = true)
    public boolean tieneMantenimientoEnCurso(Integer idServidor) {
        return ordenes.existeDelServidorEnEstados(idServidor, EstadoOrden.enCurso());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int retirarPendientes(Integer idServidor, Usuario autor, String motivo, Instant ahora) {
        List<Orden> pendientes = ordenes.findDelServidorEnEstados(idServidor, CANCELABLES);
        Actor actor = autor == null ? Actor.sistema("BAJA_SERVIDOR") : Actor.usuario(autor.getId());
        for (Orden orden : pendientes) {
            if (orden.esGrupal()) {
                // La orden grupal sigue para los demás integrantes (RF20)
                orden.retirarServidor(idServidor);
                auditoria.registrar(actor, Operacion.de("RETIRAR_SERVIDOR_DE_ORDEN", "orden", orden.getId())
                        .sobreOrden(orden.getId()).sobreServidor(idServidor).motivo(motivo));
            } else {
                orden.cancelar(autor, motivo, ahora);
                auditoria.registrar(actor, Operacion.de("CANCELAR_ORDEN", "orden", orden.getId())
                        .sobreOrden(orden.getId()).sobreServidor(idServidor).motivo(motivo));
            }
        }
        return pendientes.size();
    }
}
