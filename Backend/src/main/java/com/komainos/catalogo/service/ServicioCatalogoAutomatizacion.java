package com.komainos.catalogo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.ImplementacionScript;
import com.komainos.catalogo.model.ParametroScript;
import com.komainos.catalogo.model.Script;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.TipoScript;
import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.repository.ImplementacionScriptRepositorio;
import com.komainos.catalogo.repository.ScriptRepositorio;
import com.komainos.catalogo.repository.TareaRepositorio;
import com.komainos.catalogo.repository.VersionScriptRepositorio;
import com.komainos.ejecucion.model.MedicionInformada;
import com.komainos.ejecucion.model.ParametroEjecucion;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.ejecucion.service.ContratoAnalisis;
import com.komainos.ejecucion.service.PreparadorScript;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.VersionSistemaOperativo;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo de scripts y tareas (RF22-RF26). Un script tiene una implementación por versión de sistema operativo y cada
 * cambio de código publica una versión nueva e inmutable. Las publicaciones se serializan con un bloqueo consultivo para
 * que el número de versión y la unicidad del análisis por versión de sistema operativo no dependan de la suerte.
 */
@Service
@RequiredArgsConstructor
public class ServicioCatalogoAutomatizacion {

    private final ScriptRepositorio scripts;
    private final ImplementacionScriptRepositorio implementaciones;
    private final VersionScriptRepositorio versiones;
    private final TareaRepositorio tareas;
    private final ServicioCatalogos catalogos;
    private final ServicioParametrosSistema parametros;
    private final MetricaRepositorio metricas;
    private final UmbralRepositorio umbrales;
    private final ServicioAuditoria auditoria;
    private final ObjectMapper json;
    private final Clock reloj;

    // ---------------------------------------------------------------- Scripts (RF24, RF25)

    @Transactional(readOnly = true)
    public Page<Script> scripts(FiltroScripts filtro, Pageable pagina) {
        Page<Script> resultado = scripts.findAll(especificacion(filtro), pagina);
        resultado.forEach(ServicioCatalogoAutomatizacion::cargar);
        return resultado;
    }

    @Transactional(readOnly = true)
    public Script script(Integer id) {
        Script script = scripts.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("el script", id));
        cargar(script);
        return script;
    }

    @Transactional(readOnly = true)
    public long tareasActivasDe(Integer idScript) {
        return tareas.findByActivoTrue().stream().filter(t -> t.getScript().getId().equals(idScript)).count();
    }

    @Transactional
    public Script registrarScript(DatosScript datos, Actor actor) {
        scripts.bloquearCatalogo();
        if (scripts.existsByNombreIgnoreCase(datos.nombre().trim())) {
            throw new ConflictoException("Ya existe un script llamado «%s»".formatted(datos.nombre().trim()));
        }
        Script script = scripts.save(Script.nuevo(datos.nombre(), datos.tipo(), datos.descripcion(), datos.activo()));
        auditoria.registrar(actor, Operacion.de("REGISTRAR_SCRIPT", "script", script.getId())
                .valores(null, instantanea(script)));
        return script;
    }

    /** El tipo no cambia: las tareas y evaluaciones ya lo usaron con ese sentido */
    @Transactional
    public Script actualizarScript(Integer id, DatosScript datos, Actor actor) {
        scripts.bloquearCatalogo();
        Script script = script(id);
        if (script.getTipo() != datos.tipo()) {
            throw new ReglaNegocioException("El tipo de un script no cambia después de registrarlo");
        }
        if (scripts.existsByNombreIgnoreCaseAndIdNot(datos.nombre().trim(), id)) {
            throw new ConflictoException("Ya existe un script llamado «%s»".formatted(datos.nombre().trim()));
        }
        if (datos.activo() && !script.isActivo() && script.esAnalisis()) {
            for (ImplementacionScript i : script.getImplementaciones()) {
                exigirAnalisisUnico(i.getVersionSistemaOperativo(), id);
            }
        }
        Map<String, Object> anterior = instantanea(script);
        script.actualizar(datos.nombre(), datos.descripcion(), datos.activo());
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_SCRIPT", "script", id)
                .valores(anterior, instantanea(script)));
        return script;
    }

    /** CP080: la implementación nace con su primera versión, nunca vacía */
    @Transactional
    public ImplementacionScript implementar(Integer idScript, Integer idVersionSistemaOperativo, DatosVersion primera,
                                            Actor actor) {
        scripts.bloquearCatalogo();
        Script script = script(idScript);
        VersionSistemaOperativo version = catalogos.obtenerVersion(idVersionSistemaOperativo);
        if (!version.isActivo() || !version.getSistemaOperativo().isActivo()) {
            throw new ReglaNegocioException("La versión de sistema operativo %s está inactiva"
                    .formatted(version.descripcionCompleta()));
        }
        if (script.implementacionPara(idVersionSistemaOperativo).isPresent()) {
            throw new ConflictoException("El script ya tiene una implementación para %s"
                    .formatted(version.descripcionCompleta()));
        }
        if (script.esAnalisis() && script.isActivo()) {
            exigirAnalisisUnico(version, idScript);
        }
        ImplementacionScript implementacion = implementaciones.save(
                ImplementacionScript.nueva(script, version, Tiempo.ahora(reloj)));
        script.agregarImplementacion(implementacion);
        auditoria.registrar(actor, Operacion.de("REGISTRAR_IMPLEMENTACION_SCRIPT", "script_implementacion",
                implementacion.getId()).motivo(script.getNombre() + " · " + version.descripcionCompleta()));
        crearVersion(implementacion, primera, actor);
        return implementacion;
    }

    /** RF26: cada cambio de código es una versión nueva; las versiones fijadas en MOP no cambian */
    @Transactional
    public VersionScript publicar(Integer idImplementacion, DatosVersion datos, Actor actor) {
        scripts.bloquearCatalogo();
        ImplementacionScript implementacion = implementaciones.findConDetalleById(idImplementacion)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la implementación", idImplementacion));
        if (implementacion.getScript().esAnalisis() && implementacion.getScript().isActivo()) {
            exigirAnalisisUnico(implementacion.getVersionSistemaOperativo(), implementacion.getScript().getId());
        }
        return crearVersion(implementacion, datos, actor);
    }

    @Transactional
    public VersionScript habilitar(Integer idVersion, boolean habilitada, Actor actor) {
        scripts.bloquearCatalogo();
        VersionScript version = version(idVersion);
        ImplementacionScript implementacion = version.getImplementacion();
        if (habilitada && implementacion.getScript().esAnalisis() && implementacion.getScript().isActivo()) {
            exigirAnalisisUnico(implementacion.getVersionSistemaOperativo(), implementacion.getScript().getId());
        }
        version.habilitar(habilitada);
        auditoria.registrar(actor, Operacion.de(habilitada ? "HABILITAR_VERSION_SCRIPT" : "DESHABILITAR_VERSION_SCRIPT",
                "script_version", idVersion).motivo("%s · v%d".formatted(implementacion.getScript().getNombre(),
                version.getNumeroVersion())));
        return version;
    }

    @Transactional(readOnly = true)
    public VersionScript version(Integer id) {
        VersionScript version = versiones.findConDetalleById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la versión de script", id));
        Hibernate.initialize(version.getImplementacion().getVersiones());
        return version;
    }

    /** RF42: la última versión habilitada de la implementación para la versión de sistema operativo del servidor */
    @Transactional(readOnly = true)
    public Optional<VersionScript> compatible(Integer idScript, Integer idVersionSistemaOperativo) {
        Script script = script(idScript);
        if (!script.isActivo()) {
            return Optional.empty();
        }
        return script.implementacionPara(idVersionSistemaOperativo)
                .flatMap(ImplementacionScript::ultimaHabilitada)
                .map(v -> version(v.getId()));
    }

    /** RF24: el script de análisis activo con versión habilitada para la versión de sistema operativo */
    @Transactional(readOnly = true)
    public Optional<VersionScript> buscarAnalisis(Integer idVersionSistemaOperativo) {
        return implementaciones.findByVersionSistemaOperativoId(idVersionSistemaOperativo).stream()
                .filter(i -> i.getScript().isActivo() && i.getScript().esAnalisis())
                .flatMap(i -> i.ultimaHabilitada().stream())
                .findFirst()
                .map(v -> version(v.getId()));
    }

    /** Lo que se ejecutará: el código con el bloque de parámetros de KomainOS (vista previa del editor) */
    public VistaPrevia vistaPrevia(FamiliaSistemaOperativo familia, String contenido, List<DatosParametro> datos) {
        List<String> problemas = new ArrayList<>(problemasParametros(datos));
        PreparadorScript.problemaContenido(familia, contenido, !datos.isEmpty()).ifPresent(problemas::add);
        List<ParametroEjecucion> validos = datos.stream()
                .filter(d -> PreparadorScript.problemaNombre(d.nombre()).isEmpty())
                .map(d -> new ParametroEjecucion(d.nombre(), d.tipo(),
                        PreparadorScript.problemaValor(d.nombre(), d.tipo(), vacioANulo(d.valorPorDefecto())).isEmpty()
                                ? vacioANulo(d.valorPorDefecto()) : null))
                .toList();
        return new VistaPrevia(PreparadorScript.preparar(familia, contenido, validos), problemas);
    }

    @Transactional(readOnly = true)
    public ScriptPreparado vistaPrevia(Integer idVersion) {
        VersionScript version = version(idVersion);
        return PreparadorScript.preparar(version.getImplementacion().familia(), version.getContenido(),
                valoresDeEjecucion(version));
    }

    /**
     * Valores con los que se ejecuta una versión fijada (RF52). Las MOP no tienen valores por servidor: cada parámetro
     * toma su valor por defecto, y la publicación ya garantiza que los obligatorios lo tienen.
     */
    public static List<ParametroEjecucion> valoresDeEjecucion(VersionScript version) {
        List<ParametroEjecucion> valores = new ArrayList<>();
        for (ParametroScript p : version.getParametros()) {
            String valor = vacioANulo(p.getValorPorDefecto());
            if (valor == null && p.isObligatorio()) {
                throw new ReglaNegocioException("El parámetro obligatorio %s de la versión %d no tiene valor"
                        .formatted(p.getNombre(), version.getNumeroVersion()));
            }
            valores.add(new ParametroEjecucion(p.getNombre(), p.getTipoDato(), valor));
        }
        return valores;
    }

    /**
     * Revisa una salida de análisis contra el contrato y el catálogo: qué métricas se reconocen, cuáles no existen y
     * cuáles exigen los umbrales activos y faltan. Sirve para probar un script a mano antes de publicarlo.
     */
    @Transactional(readOnly = true)
    public RevisionSalida revisarSalida(String salida) {
        ContratoAnalisis.Revision revision = ContratoAnalisis.revisar(salida, json);
        Map<String, Metrica> catalogo = metricas.findAllByOrderByNombreAsc().stream()
                .filter(Metrica::isActivo)
                .collect(Collectors.toMap(Metrica::getCodigo, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<String> errores = new ArrayList<>(revision.errores());
        List<MetricaRevisada> revisadas = new ArrayList<>();
        Set<String> recibidas = new HashSet<>();
        for (MedicionInformada m : revision.metricas()) {
            Metrica metrica = catalogo.get(m.codigo());
            recibidas.add(m.codigo());
            if (metrica != null && metrica.isEsLista() != (m.elementos() != null)) {
                errores.add(metrica.isEsLista()
                        ? "la métrica «%s» es de lista: informe «elementos»".formatted(m.codigo())
                        : "la métrica «%s» es de valor único: informe «valor»".formatted(m.codigo()));
            }
            revisadas.add(new MetricaRevisada(m.codigo(), m.instancia(), m.valor(), m.elementos(), metrica != null,
                    metrica == null ? null : metrica.getNombre(), metrica == null ? null : metrica.getUnidad()));
        }
        List<String> faltantes = umbrales.findAplicables().stream()
                .map(u -> u.getMetrica().getCodigo())
                .filter(codigo -> !recibidas.contains(codigo))
                .distinct()
                .toList();
        faltantes.forEach(codigo -> errores.add(
                "falta la métrica «%s», que exige un umbral activo".formatted(codigo)));
        return new RevisionSalida(errores.isEmpty(), errores, revisadas, faltantes);
    }

    // ---------------------------------------------------------------- Tareas (RF22, RF23)

    @Transactional(readOnly = true)
    public Page<Tarea> tareas(Pageable pagina) {
        Page<Tarea> resultado = tareas.findAll(pagina);
        resultado.forEach(t -> {
            Hibernate.initialize(t.getIdsUmbrales());
            cargar(t.getScript());
        });
        return resultado;
    }

    @Transactional(readOnly = true)
    public Tarea tarea(Integer id) {
        Tarea tarea = tareas.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la tarea", id));
        Hibernate.initialize(tarea.getIdsUmbrales());
        cargar(tarea.getScript());
        return tarea;
    }

    /** RF41: tareas activas vinculadas a los umbrales activados, una sola vez y por prioridad */
    @Transactional(readOnly = true)
    public List<Tarea> activadas(Collection<Integer> idsUmbrales) {
        return idsUmbrales.isEmpty() ? List.of() : tareas.findActivadas(idsUmbrales);
    }

    @Transactional
    public Tarea guardarTarea(Integer id, Integer idScript, Tarea.DatosTarea datos, Set<Integer> idsUmbrales,
                              Actor actor) {
        int maximo = parametros.obtener().getMaxDuracionTareaMinutos();
        if (datos.duracionMinutos() > maximo) {
            throw new ReglaNegocioException(("La duración estimada (%d min) supera la duración máxima por tarea "
                    + "(%d min)").formatted(datos.duracionMinutos(), maximo));
        }
        boolean repetido = id == null
                ? tareas.existsByNombreIgnoreCase(datos.nombre().trim())
                : tareas.existsByNombreIgnoreCaseAndIdNot(datos.nombre().trim(), id);
        if (repetido) {
            throw new ConflictoException("Ya existe una tarea llamada «%s»".formatted(datos.nombre().trim()));
        }
        Script script = script(idScript);
        if (script.getTipo() != TipoScript.MANTENIMIENTO) {
            throw new ReglaNegocioException("Una tarea se ejecuta con un script de mantenimiento, no de análisis");
        }
        if (datos.activo() && (!script.isActivo() || !script.tieneVersionHabilitada())) {
            throw new ReglaNegocioException("El script «%s» no está activo o no tiene una versión habilitada (RF23)"
                    .formatted(script.getNombre()));
        }
        if (datos.activo() && idsUmbrales.isEmpty()) {
            throw new ReglaNegocioException(
                    "Asocie al menos un umbral: la tarea entra en la MOP cuando alguno de sus umbrales se activa");
        }
        if (umbrales.countByIdIn(idsUmbrales) != idsUmbrales.size()) {
            throw new ReglaNegocioException("Uno de los umbrales seleccionados no existe");
        }
        Tarea tarea = id == null ? Tarea.nueva() : tarea(id);
        Map<String, Object> anterior = id == null ? null : instantanea(tarea);
        tarea.actualizar(script, datos, idsUmbrales);
        tareas.save(tarea);
        auditoria.registrar(actor, Operacion.de(id == null ? "REGISTRAR_TAREA" : "ACTUALIZAR_TAREA", "tarea",
                tarea.getId()).valores(anterior, instantanea(tarea)));
        return tarea;
    }

    /** RF23: versiones de sistema operativo con al menos una versión habilitada del script */
    public static List<String> compatibilidad(Script script) {
        return script.getImplementaciones().stream()
                .filter(i -> i.ultimaHabilitada().isPresent())
                .map(i -> i.getVersionSistemaOperativo().descripcionCompleta())
                .toList();
    }

    // ---------------------------------------------------------------- Reglas internas

    private VersionScript crearVersion(ImplementacionScript implementacion, DatosVersion datos, Actor actor) {
        List<String> problemas = new ArrayList<>(problemasParametros(datos.parametros()));
        PreparadorScript.problemaContenido(implementacion.familia(), datos.contenido(), !datos.parametros().isEmpty())
                .ifPresent(problemas::add);
        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException(String.join(". ", problemas));
        }
        Integer ultimo = versiones.ultimoNumero(implementacion.getId());
        VersionScript version = VersionScript.nueva(implementacion, ultimo == null ? 1 : ultimo + 1,
                datos.contenido(), datos.notasCambio(), Tiempo.ahora(reloj));
        int orden = 0;
        for (DatosParametro p : datos.parametros()) {
            version.agregarParametro(ParametroScript.nuevo(version, p.nombre(), p.tipo(), p.obligatorio(),
                    normalizarValor(p.tipo(), p.valorPorDefecto()), ++orden));
        }
        versiones.save(version);
        implementacion.agregarVersion(version);
        auditoria.registrar(actor, Operacion.de("PUBLICAR_VERSION_SCRIPT", "script_version", version.getId())
                .motivo("%s · %s · v%d".formatted(implementacion.getScript().getNombre(),
                        implementacion.getVersionSistemaOperativo().descripcionCompleta(), version.getNumeroVersion())));
        return version;
    }

    /** Nombres válidos y únicos sin distinguir mayúsculas (PowerShell no las distingue), valores del tipo declarado */
    private static List<String> problemasParametros(List<DatosParametro> datos) {
        List<String> problemas = new ArrayList<>();
        Set<String> nombres = new HashSet<>();
        for (DatosParametro p : datos) {
            PreparadorScript.problemaNombre(p.nombre()).ifPresent(problemas::add);
            if (p.nombre() != null && !nombres.add(p.nombre().toUpperCase(Locale.ROOT))) {
                problemas.add("El parámetro %s está repetido".formatted(p.nombre()));
            }
            String valor = vacioANulo(p.valorPorDefecto());
            if (p.tipo() == null) {
                problemas.add("Indique el tipo del parámetro %s".formatted(p.nombre()));
                continue;
            }
            PreparadorScript.problemaValor(p.nombre(), p.tipo(), valor).ifPresent(problemas::add);
            if (valor == null && p.obligatorio()) {
                problemas.add(("El parámetro obligatorio %s necesita un valor por defecto: la MOP se ejecuta sin "
                        + "intervención").formatted(p.nombre()));
            }
        }
        return problemas;
    }

    /** RF24: un único script de análisis activo con versión habilitada por versión de sistema operativo */
    private void exigirAnalisisUnico(VersionSistemaOperativo version, Integer idScriptPropio) {
        implementaciones.findByVersionSistemaOperativoId(version.getId()).stream()
                .filter(i -> i.getScript().isActivo() && i.getScript().esAnalisis())
                .filter(i -> !Objects.equals(i.getScript().getId(), idScriptPropio))
                .filter(i -> i.ultimaHabilitada().isPresent())
                .findFirst()
                .ifPresent(i -> {
                    throw new ConflictoException("Ya existe el script de análisis «%s» para %s: solo se admite uno por "
                            .formatted(i.getScript().getNombre(), version.descripcionCompleta())
                            + "sistema operativo y versión");
                });
    }

    private static Specification<Script> especificacion(FiltroScripts filtro) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.tipo() != null) {
                condiciones.add(cb.equal(raiz.get("tipo"), filtro.tipo()));
            }
            if (filtro.activo() != null) {
                condiciones.add(cb.equal(raiz.get("activo"), filtro.activo()));
            }
            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                String patron = "%" + filtro.texto().trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(cb.like(cb.lower(raiz.get("nombre")), patron),
                        cb.like(cb.lower(cb.coalesce(raiz.get("descripcion"), "")), patron)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private static void cargar(Script script) {
        script.getImplementaciones().forEach(i -> {
            Hibernate.initialize(i.getVersionSistemaOperativo().getSistemaOperativo());
            i.getVersiones().forEach(v -> Hibernate.initialize(v.getParametros()));
        });
    }

    private static String normalizarValor(TipoDato tipo, String valor) {
        String v = vacioANulo(valor);
        if (v == null || tipo == TipoDato.STRING) {
            return v;
        }
        return tipo == TipoDato.BOOLEAN ? v.trim().toLowerCase(Locale.ROOT) : v.trim();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isEmpty() ? null : valor;
    }

    private static Map<String, Object> instantanea(Script s) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nombre", s.getNombre());
        v.put("tipo", s.getTipo());
        v.put("descripcion", s.getDescripcion());
        v.put("activo", s.isActivo());
        return v;
    }

    private static Map<String, Object> instantanea(Tarea t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("nombre", t.getNombre());
        v.put("idScript", t.getScript() == null ? null : t.getScript().getId());
        v.put("afectacion", t.getNivelAfectacion());
        v.put("prioridad", t.getPrioridad());
        v.put("duracionMinutos", t.getDuracionEstimadaMinutos());
        v.put("activo", t.isActivo());
        v.put("umbrales", new LinkedHashSet<>(t.getIdsUmbrales()));
        return v;
    }

    public record FiltroScripts(String texto, TipoScript tipo, Boolean activo) {

        public static FiltroScripts todos() {
            return new FiltroScripts(null, null, null);
        }
    }

    public record DatosScript(String nombre, TipoScript tipo, String descripcion, boolean activo) {
    }

    public record DatosParametro(String nombre, TipoDato tipo, boolean obligatorio, String valorPorDefecto) {
    }

    public record DatosVersion(String contenido, String notasCambio, List<DatosParametro> parametros) {

        public DatosVersion {
            parametros = parametros == null ? List.of() : List.copyOf(parametros);
        }
    }

    public record VistaPrevia(ScriptPreparado script, List<String> problemas) {
    }

    public record MetricaRevisada(String codigo, String instancia, BigDecimal valor, List<String> elementos,
                                  boolean reconocida, String nombre, String unidad) {
    }

    public record RevisionSalida(boolean valida, List<String> errores, List<MetricaRevisada> metricas,
                                 List<String> faltantes) {
    }
}
