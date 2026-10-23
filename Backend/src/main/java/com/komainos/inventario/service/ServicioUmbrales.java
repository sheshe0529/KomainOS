package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.AlcanceExcepcion;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.ObjetivoExcepcion;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.Umbral.CriterioLista;
import com.komainos.inventario.model.Umbral.LimitesNumericos;
import com.komainos.inventario.model.Umbral.ValoresExcepcion;
import com.komainos.inventario.model.UmbralExcepcion;
import com.komainos.inventario.repository.EntornoRepositorio;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.repository.ListaReferenciaRepositorio;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.NivelCriticidadRepositorio;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.repository.UmbralExcepcionRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Umbrales por métrica y sus excepciones por servidor, grupo, entorno o criticidad (RF16, HU12, R2.4). Solo el
 * administrador los modifica; cada cambio queda en la auditoría (RNF06).
 */
@Service
@RequiredArgsConstructor
public class ServicioUmbrales {

    private final UmbralRepositorio umbrales;
    private final UmbralExcepcionRepositorio excepciones;
    private final MetricaRepositorio metricas;
    private final ListaReferenciaRepositorio listas;
    private final ServidorRepositorio servidores;
    private final GrupoMantenimientoRepositorio grupos;
    private final EntornoRepositorio entornos;
    private final NivelCriticidadRepositorio criticidades;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    /** Con las excepciones y los elementos de las listas cargados: el panel muestra cuántos hay */
    @Transactional(readOnly = true)
    public List<Umbral> listar(Integer idMetrica) {
        List<Umbral> resultado = idMetrica == null ? umbrales.findTodos() : umbrales.findDeMetrica(idMetrica);
        resultado.forEach(u -> {
            Hibernate.initialize(u.getExcepciones());
            if (u.getLista() != null) {
                Hibernate.initialize(u.getLista().getItems());
            }
        });
        return resultado;
    }

    @Transactional(readOnly = true)
    public Umbral obtener(Integer id) {
        return cargado(id);
    }

    @Transactional
    public Umbral crear(DatosUmbral datos, Actor actor) {
        Metrica metrica = metricas.findById(datos.idMetrica())
                .orElseThrow(() -> RecursoNoEncontradoException.de("la métrica", datos.idMetrica()));
        String nombre = datos.nombre().trim();
        if (umbrales.existsByMetricaIdAndNombreIgnoreCase(metrica.getId(), nombre)) {
            throw new ConflictoException("La métrica %s ya tiene un umbral llamado %s".formatted(metrica.getNombre(), nombre));
        }
        Instant ahora = Tiempo.ahora(reloj);
        Umbral umbral = metrica.isEsLista()
                ? Umbral.deLista(metrica, nombre, criterio(datos), ahora)
                : Umbral.numerico(metrica, nombre, limites(datos), ahora);
        umbrales.save(umbral);
        auditoria.registrar(actor, Operacion.de("CREAR_UMBRAL", "umbral", umbral.getId())
                .valores(null, instantanea(umbral)));
        return cargado(umbral.getId());
    }

    /** La métrica no cambia; el operador tampoco mientras haya excepciones */
    @Transactional
    public Umbral actualizar(Integer id, DatosUmbral datos, Actor actor) {
        Umbral umbral = cargado(id);
        if (datos.idMetrica() != null && !datos.idMetrica().equals(umbral.getMetrica().getId())) {
            throw new ReglaNegocioException("La métrica de un umbral no se modifica: registre otro umbral");
        }
        String nombre = datos.nombre().trim();
        if (umbrales.existsByMetricaIdAndNombreIgnoreCaseAndIdNot(umbral.getMetrica().getId(), nombre, id)) {
            throw new ConflictoException("La métrica %s ya tiene otro umbral llamado %s"
                    .formatted(umbral.getMetrica().getNombre(), nombre));
        }
        Map<String, Object> anterior = instantanea(umbral);
        Instant ahora = Tiempo.ahora(reloj);
        if (umbral.esDeLista()) {
            umbral.actualizarLista(nombre, criterio(datos), ahora);
        } else {
            umbral.actualizarNumerico(nombre, limites(datos), ahora);
        }
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_UMBRAL", "umbral", id)
                .valores(anterior, instantanea(umbral)));
        return umbral;
    }

    /** Un umbral inactivo no se evalúa, ni sus excepciones */
    @Transactional
    public Umbral cambiarEstado(Integer id, boolean activar, Actor actor) {
        Umbral umbral = cargado(id);
        Instant ahora = Tiempo.ahora(reloj);
        if (activar) {
            umbral.activar(ahora);
        } else {
            umbral.desactivar(ahora);
        }
        auditoria.registrar(actor, Operacion.de(activar ? "ACTIVAR_UMBRAL" : "DESACTIVAR_UMBRAL", "umbral", id));
        return umbral;
    }

    /** Sus excepciones se eliminan con él (fk_umbral_excepcion_umbral en cascada) */
    @Transactional
    public void eliminar(Integer id, Actor actor) {
        Umbral umbral = cargado(id);
        if (umbrales.tieneEvaluaciones(id)) {
            throw new ReglaNegocioException("El umbral %s ya se aplicó en evaluaciones: desactívelo en lugar de eliminarlo"
                    .formatted(umbral.getNombre()));
        }
        Map<String, Object> anterior = instantanea(umbral);
        anterior.put("excepciones", umbral.getExcepciones().size());
        umbrales.delete(umbral);
        auditoria.registrar(actor, Operacion.de("ELIMINAR_UMBRAL", "umbral", id).valores(anterior, null));
    }

    @Transactional
    public UmbralExcepcion crearExcepcion(Integer idUmbral, DatosExcepcion datos, Actor actor) {
        Umbral umbral = cargado(idUmbral);
        ObjetivoExcepcion objetivo = objetivo(datos.alcance(), datos.idObjetivo());
        UmbralExcepcion excepcion = umbral.agregarExcepcion(objetivo, valores(datos), vacioANulo(datos.motivo()),
                Tiempo.ahora(reloj));
        excepciones.saveAndFlush(excepcion);
        auditoria.registrar(actor, operacion("CREAR_EXCEPCION_UMBRAL", excepcion).valores(null, instantanea(excepcion)));
        return excepcion;
    }

    /** Cambia los valores o la lista y el motivo; el alcance no cambia */
    @Transactional
    public UmbralExcepcion actualizarExcepcion(Integer idUmbral, Integer idExcepcion, DatosExcepcion datos, Actor actor) {
        Umbral umbral = cargado(idUmbral);
        UmbralExcepcion excepcion = excepcionDe(umbral, idExcepcion);
        if (datos.alcance() != null && (datos.alcance() != excepcion.alcance()
                || !excepcion.idObjetivo().equals(datos.idObjetivo()))) {
            throw new ReglaNegocioException("El alcance de una excepción no se modifica: registre otra excepción");
        }
        Map<String, Object> anterior = instantanea(excepcion);
        umbral.actualizarExcepcion(excepcion, valores(datos), vacioANulo(datos.motivo()), Tiempo.ahora(reloj));
        auditoria.registrar(actor, operacion("ACTUALIZAR_EXCEPCION_UMBRAL", excepcion)
                .valores(anterior, instantanea(excepcion)));
        return excepcion;
    }

    /** Una excepción desactivada se ignora al resolver: se aplica la siguiente en prioridad */
    @Transactional
    public UmbralExcepcion cambiarEstadoExcepcion(Integer idUmbral, Integer idExcepcion, boolean activar, Actor actor) {
        UmbralExcepcion excepcion = excepcionDe(cargado(idUmbral), idExcepcion);
        Instant ahora = Tiempo.ahora(reloj);
        if (activar) {
            excepcion.activar(ahora);
        } else {
            excepcion.desactivar(ahora);
        }
        auditoria.registrar(actor, operacion(activar ? "ACTIVAR_EXCEPCION_UMBRAL" : "DESACTIVAR_EXCEPCION_UMBRAL",
                excepcion));
        return excepcion;
    }

    @Transactional
    public void eliminarExcepcion(Integer idUmbral, Integer idExcepcion, Actor actor) {
        Umbral umbral = cargado(idUmbral);
        UmbralExcepcion excepcion = excepcionDe(umbral, idExcepcion);
        if (excepciones.tieneEvaluaciones(idExcepcion)) {
            throw new ReglaNegocioException("La excepción ya se aplicó en evaluaciones: desactívela en lugar de eliminarla");
        }
        Operacion operacion = operacion("ELIMINAR_EXCEPCION_UMBRAL", excepcion).valores(instantanea(excepcion), null);
        umbral.quitarExcepcion(excepcion);
        auditoria.registrar(actor, operacion);
    }

    /** Umbral con su métrica, su lista y cada excepción con su objetivo y su lista: el mapeo no dispara consultas */
    private Umbral cargado(Integer id) {
        Umbral umbral = umbrales.findConMetricaById(id).orElseThrow(() -> RecursoNoEncontradoException.de("el umbral", id));
        excepciones.findDeUmbral(id);
        Hibernate.initialize(umbral.getExcepciones());
        if (umbral.getLista() != null) {
            Hibernate.initialize(umbral.getLista().getItems());
        }
        umbral.getExcepciones().stream().filter(e -> e.getLista() != null)
                .forEach(e -> Hibernate.initialize(e.getLista().getItems()));
        return umbral;
    }

    private static UmbralExcepcion excepcionDe(Umbral umbral, Integer idExcepcion) {
        return umbral.excepcion(idExcepcion)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la excepción del umbral", idExcepcion));
    }

    /** No se definen excepciones sobre catálogos inactivos ni sobre servidores dados de baja o grupos inactivos */
    private ObjetivoExcepcion objetivo(AlcanceExcepcion alcance, Integer id) {
        if (alcance == null || id == null) {
            throw new ReglaNegocioException("Indique el alcance de la excepción: servidor, grupo, entorno o criticidad");
        }
        return switch (alcance) {
            case SERVIDOR -> {
                Servidor s = servidores.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("el servidor", id));
                s.exigirNoDadoDeBaja("definirle excepciones de umbral");
                yield ObjetivoExcepcion.de(s);
            }
            case GRUPO -> {
                GrupoMantenimiento g = grupos.findById(id)
                        .orElseThrow(() -> RecursoNoEncontradoException.de("el grupo de mantenimiento", id));
                if (g.estaInactivo()) {
                    throw new ReglaNegocioException("El grupo %s está inactivo".formatted(g.getNombre()));
                }
                yield ObjetivoExcepcion.de(g);
            }
            case ENTORNO -> {
                Entorno e = entornos.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("el entorno", id));
                if (!e.isActivo()) {
                    throw new ReglaNegocioException("El entorno %s está inactivo".formatted(e.getNombre()));
                }
                yield ObjetivoExcepcion.de(e);
            }
            case CRITICIDAD -> {
                NivelCriticidad n = criticidades.findById(id)
                        .orElseThrow(() -> RecursoNoEncontradoException.de("el nivel de criticidad", id));
                if (!n.isActivo()) {
                    throw new ReglaNegocioException("El nivel de criticidad %s está inactivo".formatted(n.getNombre()));
                }
                yield ObjetivoExcepcion.de(n);
            }
        };
    }

    private static LimitesNumericos limites(DatosUmbral datos) {
        if (datos.idLista() != null || datos.nivelFaltante() != null || datos.nivelSobrante() != null) {
            throw new ReglaNegocioException("Un umbral numérico no lleva lista de referencia ni niveles de lista");
        }
        return new LimitesNumericos(datos.operador(), datos.advertencia(), datos.critico());
    }

    private CriterioLista criterio(DatosUmbral datos) {
        if (datos.operador() != null || datos.advertencia() != null || datos.critico() != null) {
            throw new ReglaNegocioException("Un umbral de lista no lleva operador ni valores numéricos");
        }
        return new CriterioLista(lista(datos.idLista()), datos.nivelFaltante(), datos.nivelSobrante());
    }

    private ValoresExcepcion valores(DatosExcepcion datos) {
        return new ValoresExcepcion(datos.advertencia(), datos.critico(), lista(datos.idLista()));
    }

    private ListaReferencia lista(Integer id) {
        if (id == null) {
            return null;
        }
        ListaReferencia lista = listas.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la lista de referencia", id));
        Hibernate.initialize(lista.getItems());
        return lista;
    }

    /** La auditoría enlaza el servidor o el grupo afectado, si la excepción es de ese alcance */
    private static Operacion operacion(String nombre, UmbralExcepcion e) {
        Operacion operacion = Operacion.de(nombre, "umbral_excepcion", e.getId());
        return switch (e.alcance()) {
            case SERVIDOR -> operacion.sobreServidor(e.getServidor().getId());
            case GRUPO -> operacion.sobreGrupo(e.getGrupo().getId());
            default -> operacion;
        };
    }

    private static Map<String, Object> instantanea(Umbral u) {
        Map<String, Object> v = new HashMap<>();
        v.put("metrica", u.getMetrica().getCodigo());
        v.put("nombre", u.getNombre());
        v.put("activo", u.isActivo());
        if (u.esDeLista()) {
            v.put("lista", u.getLista().getNombre());
            v.put("nivelFaltante", u.getNivelFaltante() == null ? null : u.getNivelFaltante().name());
            v.put("nivelSobrante", u.getNivelSobrante() == null ? null : u.getNivelSobrante().name());
        } else {
            v.put("operador", u.getOperador().name());
            v.put("valorAdvertencia", texto(u.getValorAdvertencia()));
            v.put("valorCritico", texto(u.getValorCritico()));
        }
        return v;
    }

    private static Map<String, Object> instantanea(UmbralExcepcion e) {
        Map<String, Object> v = new HashMap<>();
        v.put("umbral", e.getUmbral().getNombre());
        v.put("alcance", e.alcance().name());
        v.put("objetivo", e.objetivo().nombre());
        v.put("valorAdvertencia", texto(e.getValorAdvertencia()));
        v.put("valorCritico", texto(e.getValorCritico()));
        v.put("lista", e.getLista() == null ? null : e.getLista().getNombre());
        v.put("motivo", e.getMotivo());
        v.put("activo", e.isActivo());
        return v;
    }

    private static String texto(BigDecimal valor) {
        return valor == null ? null : valor.stripTrailingZeros().toPlainString();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Numérico: operador y valores. De lista: idLista y niveles. idMetrica solo se usa al crear */
    public record DatosUmbral(Integer idMetrica, String nombre, OperadorComparacion operador, BigDecimal advertencia,
                              BigDecimal critico, Integer idLista, NivelAlerta nivelFaltante, NivelAlerta nivelSobrante) {
    }

    /** alcance e idObjetivo solo se usan al crear; al actualizar, si llegan, deben coincidir */
    public record DatosExcepcion(AlcanceExcepcion alcance, Integer idObjetivo, BigDecimal advertencia, BigDecimal critico,
                                 Integer idLista, String motivo) {
    }
}
