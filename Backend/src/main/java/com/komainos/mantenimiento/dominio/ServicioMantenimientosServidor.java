package com.komainos.mantenimiento.dominio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.inventario.dominio.PuertoMantenimientos;
import com.komainos.mantenimiento.infra.OrdenRepositorio;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.shared.dominio.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Implementa lo que el inventario necesita del ciclo de mantenimiento para dar
 * de baja un servidor (RF72).
 */
@Service
@RequiredArgsConstructor
public class ServicioMantenimientosServidor implements PuertoMantenimientos {

    /**
     * Estados desde los que la tabla 6 permite pasar a CANCELADA: son los
     * mantenimientos pendientes que la baja retira.
     */
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
                // La orden grupal sigue para los demas integrantes (RF20).
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
