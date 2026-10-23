package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de métricas que informan los scripts de análisis (RF16, R2.4) */
@Service
@RequiredArgsConstructor
public class ServicioMetricas {

    private final MetricaRepositorio metricas;
    private final UmbralRepositorio umbrales;
    private final ServicioAuditoria auditoria;

    @Transactional(readOnly = true)
    public List<Metrica> listar() {
        return metricas.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Metrica obtener(Integer id) {
        return metricas.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la métrica", id));
    }

    @Transactional
    public Metrica crear(DatosMetrica datos, Actor actor) {
        String codigo = datos.codigo().trim().toLowerCase(Locale.ROOT);
        if (metricas.existsByCodigoIgnoreCase(codigo)) {
            throw new ConflictoException("Ya existe una métrica con el código %s".formatted(codigo));
        }
        Metrica metrica = metricas.save(Metrica.nueva(codigo, datos.nombre().trim(), vacioANulo(datos.unidad()),
                datos.tipoDato(), datos.esLista()));
        auditoria.registrar(actor, Operacion.de("CREAR_METRICA", "metrica", metrica.getId())
                .valores(null, instantanea(metrica)));
        return metrica;
    }

    /** El código no se modifica y el tipo tampoco si ya hay umbrales o mediciones que dependen de él */
    @Transactional
    public Metrica actualizar(Integer id, DatosMetrica datos, Actor actor) {
        Metrica metrica = obtener(id);
        if (datos.codigo() != null && !datos.codigo().trim().equalsIgnoreCase(metrica.getCodigo())) {
            throw new ReglaNegocioException(("El código de la métrica no se modifica: el script de análisis lo usa para "
                    + "informar la medición"));
        }
        if (metrica.cambiaTipo(datos.tipoDato(), datos.esLista())) {
            long conUmbrales = umbrales.countByMetricaId(id);
            if (conUmbrales > 0) {
                throw new ReglaNegocioException("La métrica %s tiene %d umbral(es): su tipo ya no se puede cambiar"
                        .formatted(metrica.getNombre(), conUmbrales));
            }
            if (metricas.tieneMediciones(id)) {
                throw new ReglaNegocioException("La métrica %s ya tiene mediciones: su tipo ya no se puede cambiar"
                        .formatted(metrica.getNombre()));
            }
        }
        Map<String, Object> anterior = instantanea(metrica);
        metrica.actualizar(datos.nombre().trim(), vacioANulo(datos.unidad()), datos.tipoDato(), datos.esLista());
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_METRICA", "metrica", id)
                .valores(anterior, instantanea(metrica)));
        return metrica;
    }

    /** Una métrica inactiva no admite umbrales nuevos y sus umbrales dejan de evaluarse */
    @Transactional
    public Metrica cambiarEstado(Integer id, boolean activar, Actor actor) {
        Metrica metrica = obtener(id);
        if (activar) {
            metrica.activar();
        } else {
            metrica.desactivar();
        }
        auditoria.registrar(actor, Operacion.de(activar ? "ACTIVAR_METRICA" : "DESACTIVAR_METRICA", "metrica", id));
        return metrica;
    }

    private static Map<String, Object> instantanea(Metrica m) {
        Map<String, Object> v = new HashMap<>();
        v.put("codigo", m.getCodigo());
        v.put("nombre", m.getNombre());
        v.put("unidad", m.getUnidad());
        v.put("tipoDato", m.getTipoDato().name());
        v.put("esLista", m.isEsLista());
        return v;
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** codigo solo se usa al crear: al actualizar, si llega, debe coincidir con el registrado */
    public record DatosMetrica(String codigo, String nombre, String unidad, TipoDato tipoDato, boolean esLista) {
    }
}
