package com.komainos.mantenimiento.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RF67: doce meses observados (UTC), incluido el mes actual parcial. No se infieren estados históricos ausentes. */
@Service
@RequiredArgsConstructor
public class ServicioEvolucionDashboard {
    private static final int MESES = 12;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Evolucion consultar(Instant ahora) {
        YearMonth primero = YearMonth.from(ahora.atZone(ZoneOffset.UTC)).minusMonths(MESES - 1L);
        Timestamp inicio = Timestamp.from(primero.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        Timestamp fin = Timestamp.from(ahora.plusSeconds(1));
        Map<String, long[]> porMes = new LinkedHashMap<>();
        for (int i = 0; i < MESES; i++) porMes.put(primero.plusMonths(i).toString(), new long[3]);

        Long anteriores = jdbc.queryForObject("select count(*) from servidor where fecha_alta < ?", Long.class, inicio);
        jdbc.query("""
                select to_char(fecha_alta, 'YYYY-MM'), count(*) from servidor
                where fecha_alta >= ? and fecha_alta < ? group by 1
                """, rs -> { porMes.get(rs.getString(1))[0] = rs.getLong(2); }, inicio, fin);
        // Cada detalle es un servidor en un ciclo, no una tarea ni el cierre administrativo de la orden.
        jdbc.query("""
                select to_char(fecha_real_fin, 'YYYY-MM'),
                    count(*) filter (where estado = 'FINALIZADO'), count(*) filter (where estado = 'FALLIDO')
                from orden_detalle where fecha_real_inicio is not null and fecha_real_fin >= ?
                    and fecha_real_fin < ? and estado in ('FINALIZADO', 'FALLIDO') group by 1
                """, rs -> {
            long[] mes = porMes.get(rs.getString(1));
            mes[1] = rs.getLong(2);
            mes[2] = rs.getLong(3);
        }, inicio, fin);

        long acumulado = anteriores == null ? 0 : anteriores;
        List<ServidoresMes> servidores = new ArrayList<>();
        List<MantenimientosMes> mantenimientos = new ArrayList<>();
        for (var entrada : porMes.entrySet()) {
            long[] valores = entrada.getValue();
            acumulado += valores[0];
            servidores.add(new ServidoresMes(entrada.getKey(), acumulado, valores[0]));
            mantenimientos.add(new MantenimientosMes(entrada.getKey(), valores[1], valores[2]));
        }
        return new Evolucion(servidores, mantenimientos);
    }

    /** Registrados acumulados: incluye servidores retirados; no equivale a activos en ese mes. */
    public record ServidoresMes(String mes, long registrados, long altas) { }
    public record MantenimientosMes(String mes, long finalizados, long fallidos) { }
    public record Evolucion(List<ServidoresMes> servidores, List<MantenimientosMes> mantenimientos) { }
}
