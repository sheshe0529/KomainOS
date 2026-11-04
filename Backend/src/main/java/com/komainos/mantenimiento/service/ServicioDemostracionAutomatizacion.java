package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.catalogo.repository.ScriptRepositorio;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.VariablesSalud;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.mantenimiento.model.TipoDestinatario;
import com.komainos.mantenimiento.repository.ReglaPoliticaCambioRepositorio;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Datos opcionales de demostración (DEC-44, DEC-52), idempotentes: agregan lo que falta y nunca reemplazan una
 * configuración existente. Registran las variables de salud del R3.1 como métricas, umbrales genéricos de CPU, memoria
 * y disco, que se ajustan en Umbrales, y las reglas de la política de cambio. No traen scripts: los scripts viven en el
 * catálogo y se registran desde el panel (los de referencia están en Documentos/Scripts).
 */
@Service
@RequiredArgsConstructor
public class ServicioDemostracionAutomatizacion {

    private final ScriptRepositorio scripts;
    private final MetricaRepositorio metricas;
    private final UmbralRepositorio umbrales;
    private final ServicioCatalogos catalogos;
    private final ReglaPoliticaCambioRepositorio reglas;
    private final ServicioPoliticasCambio politicas;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    @Transactional
    public Map<String, Integer> cargar(Actor actor) {
        scripts.bloquearCatalogo();
        Map<String, Integer> agregados = new LinkedHashMap<>();
        Map<String, Metrica> variables = variablesSalud(agregados);
        umbral(variables.get("cpu_uso"), "80", "95", agregados);
        umbral(variables.get("mem_uso"), "85", "95", agregados);
        umbral(variables.get("disco_uso"), "80", "90", agregados);

        // Sin afectación: automática; parcial: responsable; total: responsable y administrador (RF43, RF44)
        for (Entorno entorno : catalogos.listarEntornos()) {
            if (!entorno.isActivo()) {
                continue;
            }
            for (NivelAfectacion nivel : NivelAfectacion.values()) {
                if (reglas.findByEntornoIdAndAfectacion(entorno.getId(), nivel).isEmpty()) {
                    politicas.guardar(null, new ServicioPoliticasCambio.DatosRegla(entorno.getId(), nivel,
                            nivel != NivelAfectacion.SIN_AFECTACION, nivel == NivelAfectacion.TOTAL, true,
                            Set.of(TipoDestinatario.ADMINISTRADOR, TipoDestinatario.RESPONSABLE), null), actor);
                    agregados.merge("reglas", 1, Integer::sum);
                }
            }
        }
        auditoria.registrar(actor, Operacion.de("CARGAR_DEMOSTRACION_AUTOMATIZACION", "automatizacion", null)
                .motivo(agregados.isEmpty() ? "Sin cambios: los ejemplos ya existían" : agregados.toString()));
        return agregados;
    }

    /** Las variables del R3.1 por clave; registra las que falten en el catálogo de métricas */
    private Map<String, Metrica> variablesSalud(Map<String, Integer> agregados) {
        Map<String, Metrica> registradas = metricas.findAllByOrderByNombreAsc().stream()
                .collect(Collectors.toMap(Metrica::getCodigo, m -> m));
        Map<String, Metrica> variables = new LinkedHashMap<>();
        for (VariablesSalud.Variable v : VariablesSalud.TODAS) {
            Metrica metrica = registradas.get(v.clave());
            if (metrica == null) {
                metrica = metricas.save(Metrica.nueva(v.clave(), v.definicion()));
                agregados.merge("metricas", 1, Integer::sum);
            }
            variables.put(v.clave(), metrica);
        }
        return variables;
    }

    /** Un umbral genérico (≥ advertencia, ≥ crítico) con el nombre de la métrica, si la métrica todavía no tiene uno */
    private void umbral(Metrica metrica, String advertencia, String critico, Map<String, Integer> agregados) {
        List<Umbral> existentes = umbrales.findDeMetrica(metrica.getId());
        boolean existe = existentes.stream()
                .anyMatch(u -> u.isActivo() || u.getNombre().equals(metrica.getNombre()));
        if (existe) {
            return;
        }
        agregados.merge("umbrales", 1, Integer::sum);
        umbrales.save(Umbral.numerico(metrica, metrica.getNombre(), new Umbral.LimitesNumericos(
                OperadorComparacion.MAYOR_IGUAL, new BigDecimal(advertencia), new BigDecimal(critico)),
                Tiempo.ahora(reloj)));
    }
}
