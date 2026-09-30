package com.komainos.planificacion.service;

import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.ProgramacionOrden;
import com.komainos.mantenimiento.repository.ProgramacionOrdenRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Panel de supervision del cronograma (RF32, HU20): ordenes programadas en un
 * rango (la vista mensual y la distribucion horaria de un dia se arman con el
 * mismo dato), limitadas al alcance del usuario.
 */
@Service
@RequiredArgsConstructor
public class ServicioCronograma {

    private static final Duration RANGO_MAXIMO = Duration.ofDays(100);

    private final ProgramacionOrdenRepositorio programaciones;

    @Transactional(readOnly = true)
    public List<ProgramacionOrden> entradas(Instant desde, Instant hasta, AlcanceUsuario alcance) {
        if (!hasta.isAfter(desde)) {
            throw new ReglaNegocioException("El fin del rango debe ser posterior a su inicio");
        }
        if (Duration.between(desde, hasta).compareTo(RANGO_MAXIMO) > 0) {
            throw new ReglaNegocioException("El rango del cronograma no puede superar los 100 días");
        }
        List<ProgramacionOrden> resultado = programaciones.findCronograma(desde, hasta, EstadoOrden.CANCELADA,
                alcance.veTodoElInventario(), alcance.usuarioId());
        // Los detalles (cantidad de servidores) se cargan en lotes (@BatchSize)
        // antes de cerrar la sesion.
        resultado.forEach(p -> Hibernate.initialize(p.getOrden().getDetalles()));
        return resultado;
    }
}
