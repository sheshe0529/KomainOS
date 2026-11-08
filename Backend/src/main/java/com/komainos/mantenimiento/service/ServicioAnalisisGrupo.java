package com.komainos.mantenimiento.service;

import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio.EventoAnalisisGrupo;
import com.komainos.seguridad.model.AlcanceUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Historial de análisis de un grupo (RF37, RF38, RF55): las revisiones periódicas del grupo y la evaluación previa y la
 * validación técnica de sus órdenes, cada evento con el análisis de cada integrante. El responsable solo ve los de sus
 * servidores, como en la ficha de la orden.
 */
@Service
@RequiredArgsConstructor
public class ServicioAnalisisGrupo {

    private final ServicioGrupo grupos;
    private final EvaluacionRepositorio evaluaciones;

    @Transactional(readOnly = true)
    public Page<AnalisisDeGrupo> historial(Integer idGrupo, AlcanceUsuario alcance, Pageable pagina) {
        grupos.visible(idGrupo, alcance);
        // La consulta ya ordena por fecha: el orden del Pageable no aplica a una consulta agrupada
        Page<EventoAnalisisGrupo> eventos = evaluaciones.eventosDeGrupo(idGrupo,
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize()));
        Predicate<Evaluacion> visible = e -> alcance.veTodoElInventario()
                || e.getServidor().esVisiblePara(alcance.usuarioId());

        List<Integer> idsRevision = eventos.stream().map(EventoAnalisisGrupo::getIdRevision).filter(Objects::nonNull)
                .distinct().toList();
        List<Integer> idsOrden = eventos.stream().map(EventoAnalisisGrupo::getIdOrden).filter(Objects::nonNull)
                .distinct().toList();
        Map<Integer, List<Evaluacion>> porRevision = idsRevision.isEmpty() ? Map.of()
                : evaluaciones.findByRevisionGrupoIdInOrderByFechaInicioAsc(idsRevision).stream().filter(visible)
                .collect(Collectors.groupingBy(e -> e.getRevisionGrupo().getId()));
        Map<String, List<Evaluacion>> porOrden = idsOrden.isEmpty() ? Map.of()
                : evaluaciones.findByDetalleOrdenIdInOrderByFechaInicioAsc(idsOrden).stream().filter(visible)
                .collect(Collectors.groupingBy(e -> clave(e.getDetalle().getOrden().getId(), e.getTipo().name())));

        return eventos.map(evento -> {
            List<Evaluacion> lista = evento.getIdRevision() != null
                    ? porRevision.getOrDefault(evento.getIdRevision(), List.of())
                    : porOrden.getOrDefault(clave(evento.getIdOrden(), evento.getTipo()), List.of());
            lista.forEach(ServicioEvaluaciones::cargar);
            Orden orden = evento.getIdOrden() == null || lista.isEmpty() ? null : lista.getFirst().getDetalle().getOrden();
            return new AnalisisDeGrupo(TipoEvaluacion.valueOf(evento.getTipo()), orden, lista);
        });
    }

    private static String clave(Integer idOrden, String tipo) {
        return idOrden + ":" + tipo;
    }

    /** @param orden nula en una revisión periódica, que no pertenece a una orden */
    public record AnalisisDeGrupo(TipoEvaluacion tipo, Orden orden, List<Evaluacion> evaluaciones) {

        /** Cuándo empezó: el primero de los análisis de sus integrantes */
        public Instant fecha() {
            return evaluaciones.stream().map(Evaluacion::getFechaInicio).min(Comparator.naturalOrder()).orElse(null);
        }
    }
}
