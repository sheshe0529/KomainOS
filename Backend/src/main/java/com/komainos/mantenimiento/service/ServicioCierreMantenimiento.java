package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.event.CicloMantenimientoCerrado;
import com.komainos.mantenimiento.model.CierreOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.model.ValidacionFuncional;
import com.komainos.mantenimiento.repository.CierreOrdenRepositorio;
import com.komainos.mantenimiento.repository.CierreOrdenRepositorio.FactorAplicable;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.IncidenciaRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.repository.ValidacionFuncionalRepositorio;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Cierre del ciclo (RF63-RF65, EA04): registra el resultado y el factor que acorta o alarga el siguiente ciclo, completa
 * una baja pendiente (RF72) y avisa a la planificación para generar la siguiente orden automática (RF29). CERRADA no
 * implica éxito: es el final del ciclo con o sin intervención.
 */
@Service
@RequiredArgsConstructor
public class ServicioCierreMantenimiento {

    public static final String INCIDENCIA = "INCIDENCIA";
    public static final String SALUD_REGULAR = "SALUD_REGULAR";
    public static final String ESTADO_NORMAL = "ESTADO_NORMAL";
    public static final String RACHA_ESTABLE = "RACHA_ESTABLE";

    private final OrdenRepositorio ordenes;
    private final CierreOrdenRepositorio cierres;
    private final IncidenciaRepositorio incidencias;
    private final ValidacionFuncionalRepositorio validaciones;
    private final EvaluacionRepositorio evaluaciones;
    private final ServicioParametrosSistema parametros;
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
        boolean conIncidencia = !incidencias.findByDetalleOrdenIdOrderByFechaAperturaAsc(orden.getId()).isEmpty();
        boolean conforme = validaciones.findByOrdenId(orden.getId()).map(ValidacionFuncional::esConforme).orElse(false);
        int minimo = parametros.obtener().getMinCiclosRachaEstable();
        int racha = conforme && !conIncidencia ? 1 + rachaAnterior(orden, minimo) : 0;
        String resultado = resultado(conIncidencia, conforme, peorNivel(orden), racha, minimo);

        FactorAplicable factor = cierres.factor(resultado);
        orden.getDetalles().forEach(OrdenDetalle::omitirSiPendiente);
        orden.cambiarEstado(EstadoOrden.CERRADA, null, motivo, ahora);
        cierres.save(CierreOrden.nuevo(orden, factor.getId(), periodicidad(orden), factor.getFactor(), ahora, motivo));
        auditoria.registrar(actor, Operacion.de("CERRAR_MANTENIMIENTO", "orden", orden.getId())
                .sobreOrden(orden.getId())
                .motivo("%s · resultado %s, factor %s".formatted(motivo, resultado, factor.getFactor().stripTrailingZeros()
                        .toPlainString())));
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

    /**
     * RF64, RF65: incidencia 0,50; salud regular 0,75; normal 1,00; racha estable 1,25. La racha cuenta este ciclo y los
     * anteriores consecutivos validados conformes y sin incidencias.
     */
    public static String resultado(boolean incidencia, boolean conforme, NivelAlerta nivel, int racha, int minimo) {
        if (incidencia) {
            return INCIDENCIA;
        }
        if (nivel != NivelAlerta.NORMAL) {
            return SALUD_REGULAR;
        }
        return conforme && racha >= minimo ? RACHA_ESTABLE : ESTADO_NORMAL;
    }

    /** El estado con que termina el ciclo: la evaluación posterior o, si no la hubo, la última del detalle */
    private NivelAlerta peorNivel(Orden orden) {
        NivelAlerta peor = NivelAlerta.NORMAL;
        for (OrdenDetalle d : orden.getDetalles()) {
            List<Evaluacion> historial = evaluaciones.findByDetalleIdOrderByFechaInicioAsc(d.getId());
            Optional<Evaluacion> ultima = historial.stream()
                    .filter(e -> e.getTipo() == TipoEvaluacion.POSTERIOR && e.estaFinalizada())
                    .reduce((a, b) -> b)
                    .or(() -> historial.stream().filter(Evaluacion::estaFinalizada).reduce((a, b) -> b));
            if (ultima.isPresent()) {
                peor = peor.peor(ultima.get().nivel());
            }
        }
        return peor;
    }

    private int rachaAnterior(Orden orden, int minimo) {
        List<CierreOrden> anteriores = orden.esGrupal()
                ? cierres.ultimosDeGrupo(orden.getGrupo().getId(), PageRequest.of(0, minimo))
                : cierres.ultimosDeServidor(orden.getServidor().getId(), PageRequest.of(0, minimo));
        int racha = 0;
        for (CierreOrden c : anteriores) {
            Integer id = c.getOrden().getId();
            boolean limpio = incidencias.findByDetalleOrdenIdOrderByFechaAperturaAsc(id).isEmpty()
                    && validaciones.findByOrdenId(id).map(ValidacionFuncional::esConforme).orElse(false);
            if (!limpio) {
                break;
            }
            racha++;
        }
        return racha;
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
