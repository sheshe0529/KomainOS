package com.komainos.mantenimiento.service;

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
import java.util.List;

/** RF66, KPI-01 a KPI-05. Cada detalle representa un mantenimiento de un servidor en un ciclo. */
@Service
@RequiredArgsConstructor
public class ServicioIndicadoresMantenimiento {
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public InformeKpi consultar(Instant desde, Instant hasta) {
        Instant actualizacion = Tiempo.ahora(reloj);
        Instant fin = hasta == null ? actualizacion.plusSeconds(1) : hasta;
        Instant inicio = desde == null ? fin.minus(Duration.ofDays(30)) : desde;
        if (!inicio.isBefore(fin)) throw new ReglaNegocioException("El inicio del periodo debe ser anterior al final");
        if (Duration.between(inicio, fin).compareTo(Duration.ofDays(3660)) > 0)
            throw new ReglaNegocioException("Seleccione un periodo de hasta diez años");
        Timestamp a = Timestamp.from(inicio), z = Timestamp.from(fin);

        var inventario = jdbc.queryForMap("""
                select count(*) filter (where estado = 'ACTIVO') as numerador, count(*) as denominador
                from servidor where estado <> 'DADO_DE_BAJA'
                """);
        var cumplimiento = jdbc.queryForMap("""
                select count(*) filter (where d.fecha_real_inicio >= o.inicio_ventana_aplicada
                    and d.fecha_real_fin <= o.fin_ventana_aplicada and d.estado = 'FINALIZADO') as numerador,
                    count(*) as denominador
                from orden_detalle d join orden o using(id_orden)
                where o.fecha_inicio_programada >= ? and o.fecha_inicio_programada < ?
                    and not exists (select 1 from historial_estado_orden h where h.id_orden = o.id_orden
                        and h.estado_nuevo in ('CANCELADA', 'RECHAZADA'))
                """, a, z);
        var estabilidad = jdbc.queryForMap("""
                select count(*) filter (where not exists (select 1 from incidencia i
                    where i.id_orden_detalle = d.id_orden_detalle)) as numerador, count(*) as denominador
                from orden_detalle d where d.fecha_real_inicio is not null
                    and d.fecha_real_fin >= ? and d.fecha_real_fin < ?
                """, a, z);
        var conformidad = jdbc.queryForMap("""
                select count(*) filter (where estado = 'CONFORME') as numerador, count(*) as denominador
                from validacion_funcional where estado in ('CONFORME', 'RECHAZADA')
                    and fecha_respuesta >= ? and fecha_respuesta < ?
                """, a, z);
        var incidencias = jdbc.queryForMap("""
                select count(*) as cantidad,
                    sum(extract(epoch from (fecha_cierre - fecha_apertura)) / 3600.0) as horas
                from incidencia where estado = 'CERRADA' and fecha_cierre >= ? and fecha_cierre < ?
                """, a, z);
        long cantidad = ((Number) incidencias.get("cantidad")).longValue();
        Double horas = cantidad == 0 ? null : ((Number) incidencias.get("horas")).doubleValue();
        return new InformeKpi(inicio, fin, actualizacion, List.of(
                porcentaje("KPI-01", "Servidores habilitados", inventario, 95),
                porcentaje("KPI-02", "Cumplimiento del cronograma", cumplimiento, 95),
                porcentaje("KPI-03", "Mantenimientos sin incidencia", estabilidad, 95),
                porcentaje("KPI-04", "Conformidad del responsable", conformidad, 90),
                new IndicadorKpi("KPI-05", "Tiempo medio de cierre de incidencias", "h", horas, cantidad,
                        horas == null ? null : horas / cantidad, null)), inventarioPorEstado(), actividad(a, z));
    }

    private List<EstadoInventario> inventarioPorEstado() {
        return jdbc.query("select estado::text, count(*) from servidor group by estado order by estado",
                (rs, fila) -> new EstadoInventario(rs.getString(1), rs.getLong(2)));
    }

    private List<ActividadDiaria> actividad(Timestamp desde, Timestamp hasta) {
        return jdbc.query("""
                with dias as (select generate_series(date_trunc('day', ?::timestamp),
                    date_trunc('day', ?::timestamp - interval '1 microsecond'), interval '1 day')::date as dia),
                altas as (select fecha_alta::date as dia, count(*) as cantidad from servidor
                    where fecha_alta >= ? and fecha_alta < ? group by 1),
                ejecutados as (select fecha_real_fin::date as dia, count(*) as cantidad from orden_detalle
                    where fecha_real_inicio is not null and fecha_real_fin >= ? and fecha_real_fin < ? group by 1),
                incidencias as (select fecha_apertura::date as dia, count(*) as cantidad from incidencia
                    where fecha_apertura >= ? and fecha_apertura < ? group by 1)
                select d.dia::text, coalesce(a.cantidad,0), coalesce(e.cantidad,0), coalesce(i.cantidad,0)
                from dias d left join altas a using(dia) left join ejecutados e using(dia)
                    left join incidencias i using(dia) order by d.dia
                """, (rs, fila) -> new ActividadDiaria(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                desde, hasta, desde, hasta, desde, hasta, desde, hasta);
    }

    private IndicadorKpi porcentaje(String codigo, String nombre, java.util.Map<String, Object> fila, double meta) {
        long n = ((Number) fila.get("numerador")).longValue(), d = ((Number) fila.get("denominador")).longValue();
        return new IndicadorKpi(codigo, nombre, "%", (double) n, d, d == 0 ? null : 100.0 * n / d, meta);
    }

    public record InformeKpi(Instant desde, Instant hasta, Instant fechaActualizacion, List<IndicadorKpi> indicadores,
                             List<EstadoInventario> inventario, List<ActividadDiaria> actividadDiaria) {}
    public record IndicadorKpi(String codigo, String nombre, String unidad, Double numerador, long denominador,
                               Double valor, Double meta) {}
    public record EstadoInventario(String estado, long cantidad) {}
    public record ActividadDiaria(String fecha, long altas, long mantenimientos, long incidencias) {}
}
