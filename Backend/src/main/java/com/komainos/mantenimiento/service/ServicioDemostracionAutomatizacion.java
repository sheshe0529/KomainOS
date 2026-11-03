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
import com.komainos.inventario.model.VariablesSalud;
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
import java.util.stream.Collectors;

/**
 * Datos opcionales de demostración (DEC-44), idempotentes: agregan lo que falta y nunca reemplazan una configuración
 * existente. Los scripts son ejemplos funcionales y seguros: el análisis solo lee y la limpieza simula por defecto
 * (SOLO_SIMULAR=true). El análisis informa las variables de salud del R3.1; los límites que se crean son genéricos y
 * se ajustan en Umbrales.
 */
@Service
@RequiredArgsConstructor
public class ServicioDemostracionAutomatizacion {

    static final String ANALISIS = "Análisis de salud del servidor";
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
        Map<String, Metrica> variables = variablesSalud(agregados);
        Integer umbralCpu = umbral(variables.get("cpu_uso"), "80", "95", agregados);
        Integer umbralMemoria = umbral(variables.get("mem_uso"), "85", "95", agregados);
        Integer umbralDisco = umbral(variables.get("disco_uso"), "80", "90", agregados);

        Script analisis = null;
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
                // El script de análisis solo se registra si alguna versión no tiene uno
                if (catalogo.buscarAnalisis(version.getId()).isEmpty()) {
                    if (analisis == null) {
                        analisis = script(ANALISIS, TipoScript.ANALISIS, "Solo lectura: informa las variables de "
                                + "salud del R3.1 (recursos, registros del sistema, seguridad y servicios)", actor, agregados);
                    }
                    implementar(analisis, version, windows ? "analisis_windows.ps1" : "analisis_linux.sh",
                            parametrosAnalisis(windows), actor, agregados);
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
                NivelAfectacion.PARCIAL, 10, 15, Set.of(umbralDisco), actor, agregados);
        tarea(MEMORIA, memoria, "Diagnóstico no destructivo del consumo de memoria",
                NivelAfectacion.SIN_AFECTACION, 20, 5, Set.of(umbralMemoria, umbralCpu), actor, agregados);

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

    /**
     * El umbral que vincula las tareas de ejemplo: el activo de la métrica si ya hay uno; si no, uno genérico
     * (≥ advertencia, ≥ crítico) con el nombre de la métrica
     */
    private Integer umbral(Metrica metrica, String advertencia, String critico, Map<String, Integer> agregados) {
        List<Umbral> existentes = umbrales.findDeMetrica(metrica.getId());
        Optional<Umbral> elegido = existentes.stream().filter(Umbral::isActivo).findFirst()
                .or(() -> existentes.stream().filter(u -> u.getNombre().equals(metrica.getNombre())).findFirst());
        if (elegido.isPresent()) {
            return elegido.get().getId();
        }
        agregados.merge("umbrales", 1, Integer::sum);
        return umbrales.save(Umbral.numerico(metrica, metrica.getNombre(), new Umbral.LimitesNumericos(
                OperadorComparacion.MAYOR_IGUAL, new BigDecimal(advertencia), new BigDecimal(critico)),
                Tiempo.ahora(reloj))).getId();
    }

    /** Los mismos valores que el script usa si se ejecuta a mano */
    private static List<DatosParametro> parametrosAnalisis(boolean windows) {
        DatosParametro muestreo = new DatosParametro("MUESTREO_SEGUNDOS", TipoDato.INTEGER, true, "5");
        DatosParametro ventana = new DatosParametro("VENTANA_HORAS", TipoDato.INTEGER, true, "24");
        return windows
                ? List.of(muestreo, ventana, new DatosParametro("EXCLUIR_PUERTOS_DINAMICOS", TipoDato.BOOLEAN, true, "true"))
                : List.of(muestreo, ventana, new DatosParametro("ARCHIVOS_CRITICOS", TipoDato.STRING, true,
                        "/etc/passwd /etc/group /etc/shadow /etc/gshadow /etc/sudoers /etc/ssh/sshd_config /etc/crontab"));
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

    /** Con saltos de línea de Unix aunque el repositorio se haya clonado con los de Windows */
    private String recurso(String nombre) {
        try (InputStream entrada = getClass().getResourceAsStream("/ansible/scripts/" + nombre)) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el script de demostración " + nombre);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el script de demostración " + nombre, ex);
        }
    }
}
