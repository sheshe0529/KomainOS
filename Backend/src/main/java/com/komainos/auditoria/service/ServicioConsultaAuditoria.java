package com.komainos.auditoria.service;

import com.komainos.auditoria.model.EventoAuditoria;
import com.komainos.auditoria.model.FiltroAuditoria;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** RNF06: consulta general de solo lectura, paginada y con parámetros enlazados. */
@Service
@RequiredArgsConstructor
public class ServicioConsultaAuditoria {
    private static final String ORIGEN = " from auditoria a left join usuario u on u.id_usuario = a.id_usuario ";
    private final JdbcTemplate jdbc;
    private final ProtectorValoresAuditoria protector;

    @Transactional(readOnly = true)
    public Page<EventoAuditoria> listar(FiltroAuditoria filtro, Pageable pagina) {
        List<Object> valores = new ArrayList<>();
        StringBuilder donde = new StringBuilder(" where 1 = 1");
        if (filtro.idUsuario() != null) agregar(donde, valores, "a.id_usuario = ?", filtro.idUsuario());
        if (filtro.sistema() != null) donde.append(filtro.sistema() ? " and a.id_usuario is null" : " and a.id_usuario is not null");
        if (filtro.operacion() != null) agregar(donde, valores, "a.operacion = ?", filtro.operacion());
        if (filtro.entidad() != null) agregar(donde, valores, "a.entidad = ?", filtro.entidad());
        if (filtro.desde() != null) agregar(donde, valores, "a.fecha_hora >= ?", Timestamp.from(filtro.desde()));
        if (filtro.hasta() != null) agregar(donde, valores, "a.fecha_hora < ?", Timestamp.from(filtro.hasta()));
        if (filtro.texto() != null) {
            // La búsqueda recorre metadatos; nunca inspecciona valores anteriores/nuevos que pudieran ser sensibles.
            String patron = "%" + filtro.texto().toLowerCase(Locale.ROOT).replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_") + "%";
            agregar(donde, valores, "lower(concat_ws(' ', a.operacion, a.entidad, a.motivo, a.proceso, "
                    + "case when a.id_usuario is null then 'Sistema' else '' end, "
                    + "u.codigo, u.nombre_completo, a.id_entidad::text, a.id_servidor::text, "
                    + "a.id_grupo_mantenimiento::text, a.id_orden::text)) like ? escape '!'", patron);
        }
        Long total = jdbc.queryForObject("select count(*)" + ORIGEN + donde, Long.class, valores.toArray());
        PageRequest efectiva = PageRequest.of(pagina.getPageNumber(), Math.min(100, pagina.getPageSize()));
        valores.add(efectiva.getPageSize());
        valores.add(efectiva.getOffset());
        var eventos = jdbc.query("select a.*, u.codigo, u.nombre_completo" + ORIGEN + donde
                        + " order by a.fecha_hora desc, a.id_auditoria desc limit ? offset ?",
                (rs, n) -> new EventoAuditoria(rs.getInt("id_auditoria"), rs.getTimestamp("fecha_hora").toInstant(),
                        rs.getObject("id_usuario", Integer.class), rs.getString("codigo"), rs.getString("nombre_completo"),
                        rs.getString("proceso"), rs.getString("operacion"), rs.getString("entidad"),
                        rs.getObject("id_entidad", Integer.class), rs.getObject("id_servidor", Integer.class),
                        rs.getObject("id_grupo_mantenimiento", Integer.class), rs.getObject("id_orden", Integer.class),
                        rs.getString("motivo"), protector.leer(rs.getString("valor_anterior")),
                        protector.leer(rs.getString("valor_nuevo"))), valores.toArray());
        return new PageImpl<>(eventos, efectiva, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Opciones filtros() {
        List<Autor> autores = jdbc.query("""
                select distinct u.id_usuario, u.codigo, u.nombre_completo
                from auditoria a join usuario u using(id_usuario) order by u.nombre_completo, u.id_usuario
                """, (rs, n) -> new Autor(rs.getInt(1), rs.getString(2), rs.getString(3)));
        return new Opciones(autores, jdbc.queryForList("select distinct operacion from auditoria order by operacion", String.class),
                jdbc.queryForList("select distinct entidad from auditoria order by entidad", String.class));
    }

    private static void agregar(StringBuilder donde, List<Object> valores, String condicion, Object valor) {
        donde.append(" and ").append(condicion);
        valores.add(valor);
    }

    public record Autor(Integer id, String codigo, String nombre) { }
    public record Opciones(List<Autor> usuarios, List<String> operaciones, List<String> entidades) { }
}
