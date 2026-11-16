package com.komainos.planificacion.service;

import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.service.PuertoOrdenesProgramadas;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.planificacion.config.PropiedadesPlanificacion;
import com.komainos.shared.model.Intervalo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Qué órdenes pendientes quedarían fuera de una ventana nueva del servidor (RF18, DEC-60). Cambiar la ventana no las
 * reprograma: conservan su programación y quien hace el cambio lo confirma expresamente.
 */
@Service
@RequiredArgsConstructor
public class ServicioOrdenesFueraDeVentana implements PuertoOrdenesProgramadas {

    /** Programadas y aún sin ejecutar: las que ocupan el cronograma, salvo la que ya está en ejecución */
    private static final Set<EstadoOrden> PENDIENTES = EnumSet.of(EstadoOrden.PROGRAMADA, EstadoOrden.REPROGRAMADA,
            EstadoOrden.EN_EVALUACION, EstadoOrden.PENDIENTE_AUTORIZACION, EstadoOrden.AUTORIZADA, EstadoOrden.EN_COLA);

    private final OrdenRepositorio ordenes;
    private final PropiedadesPlanificacion propiedades;

    @Override
    @Transactional(readOnly = true)
    public List<OrdenFueraDeVentana> fueraDeVentana(Integer idServidor, CalendarioSemanal ventana) {
        return ordenes.findDelServidorEnEstados(idServidor, PENDIENTES).stream()
                .flatMap(orden -> tramo(orden, idServidor)
                        .filter(tramo -> !cabe(tramo, ventana))
                        .map(tramo -> new OrdenFueraDeVentana(orden.getId(), orden.getCodigo(), orden.getEstado().name(),
                                tramo.inicio(), tramo.fin()))
                        .stream())
                .sorted(Comparator.comparing(OrdenFueraDeVentana::inicio).thenComparing(OrdenFueraDeVentana::codigo))
                .toList();
    }

    /** El horario del servidor en la orden: su tramo si lo tiene, si no el de la orden completa */
    private static Optional<Intervalo> tramo(Orden orden, Integer idServidor) {
        return orden.getDetalles().stream()
                .filter(d -> d.getServidor().getId().equals(idServidor) && d.getEstado().ocupaServidor()
                        && d.getFechaRealInicio() == null)
                .findFirst()
                .map(d -> intervalo(orden, d));
    }

    private static Intervalo intervalo(Orden orden, OrdenDetalle d) {
        return d.getFechaPrevistaInicio() != null && d.getFechaPrevistaFin() != null
                ? new Intervalo(d.getFechaPrevistaInicio(), d.getFechaPrevistaFin())
                : new Intervalo(orden.getFechaInicioProgramada(), orden.getFechaFinProgramada());
    }

    private boolean cabe(Intervalo tramo, CalendarioSemanal ventana) {
        return ventana.proyectar(tramo.inicio().minus(Duration.ofDays(8)), tramo.fin().plus(Duration.ofDays(1)),
                        propiedades.zona())
                .stream().anyMatch(v -> v.contiene(tramo));
    }
}
