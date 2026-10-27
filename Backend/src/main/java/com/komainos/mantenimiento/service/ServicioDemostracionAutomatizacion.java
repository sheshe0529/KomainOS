package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.ImplementacionScript;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.catalogo.model.Script;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.TipoScript;
import com.komainos.catalogo.repository.ImplementacionScriptRepositorio;
import com.komainos.catalogo.repository.ScriptRepositorio;
import com.komainos.catalogo.repository.TareaRepositorio;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosParametro;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosVersion;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.SistemaOperativo;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.VersionSistemaOperativo;
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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Datos opcionales de demostración (DEC-44), idempotentes: agregan lo que falta y nunca reemplazan una configuración
 * existente. Los scripts son ejemplos funcionales y seguros: el análisis solo lee y la limpieza simula por defecto
 * (SOLO_SIMULAR=true). Sus métricas y límites son provisionales hasta definir las variables de la organización.
 */
@Service
@RequiredArgsConstructor
public class ServicioDemostracionAutomatizacion {

    static final String ANALISIS = "Análisis básico de salud";
    static final String LIMPIEZA = "Liberar archivos temporales";
    static final String MEMORIA = "Revisar consumo de memoria";

    private final ScriptRepositorio scripts;
    private final ImplementacionScriptRepositorio implementaciones;
    private final MetricaRepositorio metricas;
    private final UmbralRepositorio umbrales;
    private final TareaRepositorio tareas;
    private final ServicioCatalogos catalogos;
    private final ServicioCatalogoAutomatizacion catalogo;
    private final ReglaPoliticaCambioRepositorio reglas;
    private final ServicioPoliticasCambio politicas;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    @Transactional
    public Map<String, Integer> cargar(Actor actor) {
        scripts.bloquearCatalogo();
        Map<String, Integer> agregados = new LinkedHashMap<>();
        Map<String, Integer> idsUmbral = new LinkedHashMap<>();
        for (String[] m : new String[][]{{"cpu_uso_pct", "Uso de CPU"}, {"memoria_uso_pct", "Uso de memoria"},
                {"disco_uso_pct", "Uso de disco"}}) {
            idsUmbral.put(m[0], umbralDemostracion(m[0], m[1], agregados));
        }

        Script analisis = script(ANALISIS, TipoScript.ANALISIS,
                "Ejemplo de solo lectura: CPU, memoria, disco, SWAP, carga, puertos, servicios y usuarios", actor, agregados);
        Script limpieza = script(LIMPIEZA, TipoScript.MANTENIMIENTO,
                "Ejemplo con parámetros; por defecto solo simula y no elimina archivos", actor, agregados);
        Script memoria = script(MEMORIA, TipoScript.MANTENIMIENTO,
                "Ejemplo de diagnóstico: memoria libre y procesos con más consumo", actor, agregados);

        for (SistemaOperativo so : catalogos.listarSistemasOperativos()) {
            if (!so.isActivo()) {
                continue;
            }
            boolean windows = so.getFamilia() == FamiliaSistemaOperativo.WINDOWS;
            for (VersionSistemaOperativo version : so.getVersiones()) {
                if (!version.isActivo()) {
                    continue;
                }
                if (catalogo.buscarAnalisis(version.getId()).isEmpty()) {
                    implementar(analisis, version, windows ? "analisis_windows.ps1" : "analisis_linux.sh", List.of(),
                            actor, agregados);
                }
                implementar(limpieza, version, windows ? "liberar_temporales_windows.ps1" : "liberar_temporales_linux.sh",
                        List.of(new DatosParametro("RUTA_TEMPORAL", TipoDato.STRING, true, windows ? "C:\\Windows\\Temp" : "/tmp"),
                                new DatosParametro("DIAS_RETENCION", TipoDato.INTEGER, true, "7"),
                                new DatosParametro("SOLO_SIMULAR", TipoDato.BOOLEAN, true, "true")), actor, agregados);
                implementar(memoria, version, windows ? "revisar_memoria_windows.ps1" : "revisar_memoria_linux.sh",
                        List.of(new DatosParametro("TOP_PROCESOS", TipoDato.INTEGER, true, "5")), actor, agregados);
            }
        }

        tarea(LIMPIEZA, limpieza, "Libera espacio en la carpeta temporal (simulación por defecto)",
                NivelAfectacion.PARCIAL, 10, 15, Set.of(idsUmbral.get("disco_uso_pct")), actor, agregados);
        tarea(MEMORIA, memoria, "Diagnóstico no destructivo del consumo de memoria",
                NivelAfectacion.SIN_AFECTACION, 20, 5, Set.of(idsUmbral.get("memoria_uso_pct"), idsUmbral.get("cpu_uso_pct")),
                actor, agregados);

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

    /** Umbral ≥ 80 % advertencia y ≥ 95 % crítico, solo si la métrica no tiene ya uno de demostración */
    private Integer umbralDemostracion(String codigo, String nombre, Map<String, Integer> agregados) {
        Metrica metrica = metricas.findAllByOrderByNombreAsc().stream()
                .filter(m -> m.getCodigo().equals(codigo))
                .findFirst()
                .orElseGet(() -> {
                    agregados.merge("metricas", 1, Integer::sum);
                    return metricas.save(Metrica.nueva(codigo, nombre, "%", TipoDato.DECIMAL, false));
                });
        String nombreUmbral = "Demostración · " + metrica.getNombre();
        Optional<Umbral> existente = umbrales.findDeMetrica(metrica.getId()).stream()
                .filter(u -> u.getNombre().equals(nombreUmbral))
                .findFirst();
        if (existente.isPresent()) {
            return existente.get().getId();
        }
        agregados.merge("umbrales", 1, Integer::sum);
        return umbrales.save(Umbral.numerico(metrica, nombreUmbral, new Umbral.LimitesNumericos(
                OperadorComparacion.MAYOR_IGUAL, new BigDecimal("80"), new BigDecimal("95")), Tiempo.ahora(reloj))).getId();
    }

    private Script script(String nombre, TipoScript tipo, String descripcion, Actor actor, Map<String, Integer> agregados) {
        return scripts.findAll().stream()
                .filter(s -> s.getNombre().equals(nombre))
                .findFirst()
                .orElseGet(() -> {
                    agregados.merge("scripts", 1, Integer::sum);
                    return catalogo.registrarScript(new DatosScript(nombre, tipo, descripcion, true), actor);
                });
    }

    private void implementar(Script script, VersionSistemaOperativo version, String recurso,
                             List<DatosParametro> parametros, Actor actor, Map<String, Integer> agregados) {
        boolean existe = implementaciones.findByVersionSistemaOperativoId(version.getId()).stream()
                .map(ImplementacionScript::getScript)
                .anyMatch(s -> s.getId().equals(script.getId()));
        if (existe) {
            return;
        }
        catalogo.implementar(script.getId(), version.getId(),
                new DatosVersion(recurso(recurso), "Versión de demostración inicial", parametros), actor);
        agregados.merge("implementaciones", 1, Integer::sum);
    }

    private void tarea(String nombre, Script script, String descripcion, NivelAfectacion afectacion, int prioridad,
                       int duracion, Set<Integer> idsUmbrales, Actor actor, Map<String, Integer> agregados) {
        boolean existe = tareas.findAll().stream().map(Tarea::getNombre).anyMatch(nombre::equals);
        if (existe || !catalogo.script(script.getId()).tieneVersionHabilitada()) {
            return;
        }
        catalogo.guardarTarea(null, script.getId(), new Tarea.DatosTarea(nombre, descripcion, afectacion, prioridad,
                duracion, true), idsUmbrales, actor);
        agregados.merge("tareas", 1, Integer::sum);
    }

    private String recurso(String nombre) {
        try (InputStream entrada = getClass().getResourceAsStream("/ansible/scripts/" + nombre)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el script de demostración " + nombre);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el script de demostración " + nombre, ex);
        }
    }
}
