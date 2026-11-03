package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.service.ServicioBitacora.ResultadoMantenimiento;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RF67: series observadas, sin interpolar ni sustituir mediciones ausentes por cero. Recursos y ejecuciones tienen
 * ventana fija; órdenes e incidencias usan el periodo de los indicadores, y los mantenimientos por mes, seis meses.
 */
@Service
@RequiredArgsConstructor
public class ServicioGraficosAutomatizacion {

    private static final int MESES = 6;

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    /** Sin periodo, los últimos 30 días, igual que los indicadores (RF66) */
    @Transactional(readOnly = true)
    public ResumenGraficos consultar(Instant desde, Instant hasta) {
        Instant ahora = Tiempo.ahora(reloj);
        Instant finPeriodo = hasta == null ? ahora.plusSeconds(1) : hasta;
        Instant inicioPeriodo = desde == null ? finPeriodo.minus(Duration.ofDays(30)) : desde;
        if (!inicioPeriodo.isBefore(finPeriodo)) {
            throw new ReglaNegocioException("El inicio del periodo debe ser anterior a su fin");
        }
        Instant inicioHoras = ahora.truncatedTo(ChronoUnit.HOURS).minus(23, ChronoUnit.HOURS);
        Instant inicioDias = ahora.truncatedTo(ChronoUnit.DAYS).minus(6, ChronoUnit.DAYS);
        Timestamp fin = Timestamp.from(ahora.plusSeconds(1));
        var recursos = jdbc.query("""
                with horas as (select generate_series(?::timestamp, date_trunc('hour', ?::timestamp),
                    interval '1 hour') as hora),
                muestras as (select date_trunc('hour', e.fecha_fin) as hora, e.id_servidor,
                    avg(m.valor) filter (where mt.codigo = 'cpu_uso') as cpu,
                    avg(m.valor) filter (where mt.codigo = 'mem_uso') as memoria
                    from medicion_metrica m join evaluacion e using(id_evaluacion)
                    join metrica mt using(id_metrica) join servidor s using(id_servidor)
                    where e.estado = 'FINALIZADA' and s.estado = 'ACTIVO' and e.fecha_fin >= ?
                        and e.fecha_fin < ? and mt.codigo in ('cpu_uso','mem_uso')
                    group by 1, 2),
                promedios as (select hora, avg(cpu) as cpu, avg(memoria) as memoria, count(*) as servidores
                    from muestras group by hora)
                select to_char(h.hora,'YYYY-MM-DD"T"HH24:MI:SS"Z"'), p.cpu, p.memoria,
                    coalesce(p.servidores,0) from horas h left join promedios p using(hora) order by h.hora
                """, (rs, fila) -> new RecursoHora(rs.getString(1),
                rs.getObject(2) == null ? null : rs.getDouble(2),
                rs.getObject(3) == null ? null : rs.getDouble(3), rs.getLong(4)),
                Timestamp.from(inicioHoras), Timestamp.from(ahora), Timestamp.from(inicioHoras), fin);
        var ejecuciones = jdbc.query("""
                with dias as (select generate_series(?::timestamp, date_trunc('day', ?::timestamp),
                    interval '1 day')::date as dia),
                tareas as (select t.fecha_fin::date as dia, t.estado,
                    exists(select 1 from validacion_tecnica v where v.id_orden_detalle = m.id_orden_detalle
                        and v.resultado = 'NO_CONFORME') as advertencia
                    from mop_tarea t join mop m using(id_mop)
                    where t.fecha_fin >= ? and t.fecha_fin < ? and t.estado in ('FINALIZADA','FALLIDA'))
                select d.dia::text,
                    count(*) filter (where t.estado = 'FINALIZADA' and not t.advertencia),
                    count(*) filter (where t.estado = 'FINALIZADA' and t.advertencia),
                    count(*) filter (where t.estado = 'FALLIDA')
                from dias d left join tareas t using(dia) group by d.dia order by d.dia
                """, (rs, fila) -> new EjecucionDia(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                Timestamp.from(inicioDias), Timestamp.from(ahora), Timestamp.from(inicioDias), fin);
        Timestamp desdePeriodo = Timestamp.from(inicioPeriodo);
        Timestamp hastaPeriodo = Timestamp.from(finPeriodo);
        var ordenes = jdbc.query("""
                select estado::text, count(*) from orden
                where fecha_creacion >= ? and fecha_creacion < ?
                group by estado order by count(*) desc, estado
                """, (rs, fila) -> new Conteo(rs.getString(1), rs.getLong(2)), desdePeriodo, hastaPeriodo);
        var incidencias = jdbc.query("""
                select origen::text, count(*) filter (where estado <> 'CERRADA'), count(*) filter (where estado = 'CERRADA')
                from incidencia where fecha_apertura >= ? and fecha_apertura < ?
                group by origen order by count(*) desc, origen
                """, (rs, fila) -> new IncidenciasOrigen(rs.getString(1), rs.getLong(2), rs.getLong(3)),
                desdePeriodo, hastaPeriodo);
        return new ResumenGraficos(ahora, inicioPeriodo, finPeriodo, recursos, ejecuciones, mantenimientosPorMes(ahora),
                ordenes, incidencias, salud());
    }

    /**
     * Cada servidor de una orden cerrada cuenta como un mantenimiento, en el mes en que se cerró (UTC). Se clasifica
     * con la misma regla que la bitácora: el estado anterior al cierre dice si se hizo
     */
    private List<ResultadoMes> mantenimientosPorMes(Instant ahora) {
        YearMonth actual = YearMonth.from(ahora.atZone(ZoneOffset.UTC));
        YearMonth primero = actual.minusMonths(MESES - 1L);
        Map<String, long[]> meses = new LinkedHashMap<>();
        for (int i = 0; i < MESES; i++) {
            meses.put(primero.plusMonths(i).toString(), new long[4]);
        }
        jdbc.query("""
                select to_char(h.fecha_hora, 'YYYY-MM'), h.estado_anterior::text,
                    exists(select 1 from incidencia i where i.id_orden_detalle = d.id_orden_detalle),
                    exists(select 1 from mop_tarea t join mop m using(id_mop)
                        where m.id_orden_detalle = d.id_orden_detalle and t.estado in ('FINALIZADA','FALLIDA'))
                from historial_estado_orden h join orden_detalle d using(id_orden)
                where h.estado_nuevo = 'CERRADA' and h.fecha_hora >= ?
                """, rs -> {
            long[] cuentas = meses.get(rs.getString(1));
            if (cuentas == null) {
                return;
            }
            String anterior = rs.getString(2);
            ResultadoMantenimiento r = ServicioBitacora.resultado(EstadoOrden.CERRADA,
                    anterior == null ? null : EstadoOrden.valueOf(anterior), rs.getBoolean(4), rs.getBoolean(3));
            switch (r) {
                case EXITOSO -> cuentas[0]++;
                case SIN_INTERVENCION -> cuentas[1]++;
                case CON_INCIDENCIA -> cuentas[2]++;
                default -> cuentas[3]++;
            }
        }, Timestamp.from(primero.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        List<ResultadoMes> resultado = new ArrayList<>();
        meses.forEach((mes, c) -> resultado.add(new ResultadoMes(mes, c[0], c[1], c[2], c[3])));
        return resultado;
    }

    /** Nivel del último análisis concluyente de cada servidor activo: el peor de sus umbrales */
    private SaludServidores salud() {
        return jdbc.queryForObject("""
                with ultima as (
                    select distinct on (e.id_servidor) e.id_servidor, e.id_evaluacion
                    from evaluacion e join servidor s using(id_servidor)
                    where s.estado = 'ACTIVO' and e.estado = 'FINALIZADA'
                    order by e.id_servidor, e.fecha_fin desc, e.id_evaluacion desc),
                niveles as (
                    select u.id_servidor, coalesce(max(case eu.nivel when 'CRITICO' then 2
                        when 'ADVERTENCIA' then 1 else 0 end), 0) as nivel
                    from ultima u left join medicion_metrica m using(id_evaluacion)
                        left join evaluacion_umbral eu using(id_medicion_metrica)
                    group by u.id_servidor)
                select count(*) filter (where nivel = 0), count(*) filter (where nivel = 1),
                    count(*) filter (where nivel = 2),
                    (select count(*) from servidor where estado = 'ACTIVO') - count(*)
                from niveles
                """, (rs, fila) -> new SaludServidores(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)));
    }

    /**
     * @param desde inicio del periodo de órdenes e incidencias
     * @param hasta fin exclusivo del periodo
     */
    public record ResumenGraficos(Instant fechaActualizacion, Instant desde, Instant hasta, List<RecursoHora> recursos,
                                  List<EjecucionDia> ejecuciones, List<ResultadoMes> mantenimientosPorMes,
                                  List<Conteo> ordenesPorEstado, List<IncidenciasOrigen> incidenciasPorOrigen,
                                  SaludServidores saludServidores) {
    }
    /** @param mes AAAA-MM */
    public record ResultadoMes(String mes, long exitosos, long sinIntervencion, long conIncidencia, long noRealizados) {
    }
    public record Conteo(String clave, long cantidad) {
    }
    public record IncidenciasOrigen(String origen, long abiertas, long cerradas) {
    }
    public record SaludServidores(long normal, long advertencia, long critico, long sinAnalisis) {
    }
    public record RecursoHora(String hora, Double cpu, Double memoria, long servidores) {
    }
    public record EjecucionDia(String fecha, long exito, long advertencia, long error) {
    }
}
