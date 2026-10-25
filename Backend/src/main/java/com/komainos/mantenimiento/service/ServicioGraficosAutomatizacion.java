package com.komainos.mantenimiento.service;

import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** RF67: series observadas, sin interpolar ni sustituir mediciones ausentes por cero. */
@Service
@RequiredArgsConstructor
public class ServicioGraficosAutomatizacion {
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public ResumenGraficos consultar() {
        Instant ahora = Tiempo.ahora(reloj);
        Instant inicioHoras = ahora.truncatedTo(ChronoUnit.HOURS).minus(23, ChronoUnit.HOURS);
        Instant inicioDias = ahora.truncatedTo(ChronoUnit.DAYS).minus(6, ChronoUnit.DAYS);
        Timestamp fin = Timestamp.from(ahora.plusSeconds(1));
        var recursos = jdbc.query("""
                with horas as (select generate_series(?::timestamp, date_trunc('hour', ?::timestamp),
                    interval '1 hour') as hora),
                muestras as (select date_trunc('hour', e.fecha_fin) as hora, e.id_servidor,
                    avg(m.valor) filter (where mt.codigo = 'cpu_uso_pct') as cpu,
                    avg(m.valor) filter (where mt.codigo = 'memoria_uso_pct') as memoria
                    from medicion_metrica m join evaluacion e using(id_evaluacion)
                    join metrica mt using(id_metrica) join servidor s using(id_servidor)
                    where e.estado = 'FINALIZADA' and s.estado = 'ACTIVO' and e.fecha_fin >= ?
                        and e.fecha_fin < ? and mt.codigo in ('cpu_uso_pct','memoria_uso_pct')
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
                """, (rs, fila) -> new EjecucionDia(rs.getString(1),rs.getLong(2),rs.getLong(3),rs.getLong(4)),
                Timestamp.from(inicioDias), Timestamp.from(ahora), Timestamp.from(inicioDias), fin);
        return new ResumenGraficos(ahora, recursos, ejecuciones);
    }

    public record ResumenGraficos(Instant fechaActualizacion, List<RecursoHora> recursos, List<EjecucionDia> ejecuciones) {}
    public record RecursoHora(String hora, Double cpu, Double memoria, long servidores) {}
    public record EjecucionDia(String fecha, long exito, long advertencia, long error) {}
}
