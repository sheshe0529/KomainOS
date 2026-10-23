package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.ListaReferencia.CambioElementos;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralExcepcion;
import com.komainos.inventario.repository.ConteoPorId;
import com.komainos.inventario.repository.ListaReferenciaRepositorio;
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

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Listas de referencia con las que se comparan las métricas de lista, y sus elementos con vigencia (R2.4) */
@Service
@RequiredArgsConstructor
public class ServicioListasReferencia {

    private final ListaReferenciaRepositorio listas;
    private final UmbralRepositorio umbrales;
    private final UmbralExcepcionRepositorio excepciones;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public List<ListaConUso> listar() {
        Map<Integer, Long> enUmbrales = conteo(umbrales.contarPorLista());
        Map<Integer, Long> enExcepciones = conteo(excepciones.contarPorLista());
        List<ListaReferencia> todas = listas.findAllByOrderByNombreAsc();
        // Con BatchSize los elementos de todas las listas se cargan en pocas consultas
        todas.forEach(l -> Hibernate.initialize(l.getItems()));
        return todas.stream()
                .map(l -> new ListaConUso(l, enUmbrales.getOrDefault(l.getId(), 0L),
                        enExcepciones.getOrDefault(l.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ListaConUso obtener(Integer id) {
        ListaReferencia lista = buscar(id);
        Hibernate.initialize(lista.getItems());
        return new ListaConUso(lista, umbrales.findByListaIdOrderByNombreAsc(id).size(),
                excepciones.findDeLista(id).size());
    }

    /** Se guarda primero sin elementos: la clave de cada elemento incluye el id de la lista */
    @Transactional
    public ListaReferencia crear(DatosLista datos, Actor actor) {
        String nombre = datos.nombre().trim();
        if (listas.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("Ya existe una lista con el nombre %s".formatted(nombre));
        }
        Instant ahora = Tiempo.ahora(reloj);
        ListaReferencia lista = listas.save(ListaReferencia.nueva(nombre, datos.tipoDato(), datos.ignoraMayusculas(), ahora));
        lista.reemplazarElementos(datos.elementos(), reloj.instant());
        auditoria.registrar(actor, Operacion.de("CREAR_LISTA_REFERENCIA", "lista_referencia", lista.getId())
                .valores(null, instantanea(lista)));
        return lista;
    }

    /** El tipo no cambia; los elementos quitados quedan con su fecha de baja y los nuevos con su fecha de alta */
    @Transactional
    public ListaReferencia actualizar(Integer id, DatosLista datos, Actor actor) {
        ListaReferencia lista = buscar(id);
        if (datos.tipoDato() != null && datos.tipoDato() != lista.getTipoDato()) {
            throw new ReglaNegocioException(("El tipo de dato de la lista no se modifica: los umbrales que la usan dependen "
                    + "de que coincida con el de su métrica"));
        }
        String nombre = datos.nombre().trim();
        if (listas.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ConflictoException("Ya existe otra lista con el nombre %s".formatted(nombre));
        }
        Map<String, Object> anterior = instantanea(lista);
        Instant ahora = Tiempo.ahora(reloj);
        lista.renombrar(nombre, ahora);
        lista.definirIgnoraMayusculas(datos.ignoraMayusculas());
        CambioElementos cambio = lista.reemplazarElementos(datos.elementos(), reloj.instant());
        Map<String, Object> nuevo = instantanea(lista);
        nuevo.put("agregados", cambio.agregados());
        nuevo.put("retirados", cambio.retirados());
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_LISTA_REFERENCIA", "lista_referencia", id)
                .valores(anterior, nuevo));
        return lista;
    }

    /** Desactivarla no se bloquea: lo que ya la usa la conserva, pero no se asigna a umbrales ni excepciones nuevas */
    @Transactional
    public ListaReferencia cambiarEstado(Integer id, boolean activar, Actor actor) {
        ListaReferencia lista = buscar(id);
        if (activar) {
            lista.activar();
        } else {
            lista.desactivar();
        }
        auditoria.registrar(actor, Operacion.de(activar ? "ACTIVAR_LISTA_REFERENCIA" : "DESACTIVAR_LISTA_REFERENCIA",
                "lista_referencia", id));
        return lista;
    }

    /** Solo una lista sin uso: los umbrales y excepciones la referencian con RESTRICT, igual que las evaluaciones */
    @Transactional
    public void eliminar(Integer id, Actor actor) {
        ListaReferencia lista = buscar(id);
        List<String> enUmbrales = umbrales.findByListaIdOrderByNombreAsc(id).stream().map(Umbral::getNombre).toList();
        if (!enUmbrales.isEmpty()) {
            throw new ReglaNegocioException("La lista %s es la de los umbrales %s: asígneles otra antes de eliminarla"
                    .formatted(lista.getNombre(), String.join(", ", enUmbrales)));
        }
        List<String> enExcepciones = excepciones.findDeLista(id).stream()
                .map(UmbralExcepcion::getUmbral).map(Umbral::getNombre).distinct().sorted().toList();
        if (!enExcepciones.isEmpty()) {
            throw new ReglaNegocioException("La lista %s se usa en excepciones de los umbrales %s: cámbielas antes de eliminarla"
                    .formatted(lista.getNombre(), String.join(", ", enExcepciones)));
        }
        if (listas.tieneEvaluaciones(id)) {
            throw new ReglaNegocioException("La lista %s ya se aplicó en evaluaciones: desactívela en lugar de eliminarla"
                    .formatted(lista.getNombre()));
        }
        Map<String, Object> anterior = instantanea(lista);
        listas.delete(lista);
        auditoria.registrar(actor, Operacion.de("ELIMINAR_LISTA_REFERENCIA", "lista_referencia", id)
                .valores(anterior, null));
    }

    private ListaReferencia buscar(Integer id) {
        return listas.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la lista de referencia", id));
    }

    private static Map<Integer, Long> conteo(List<ConteoPorId> filas) {
        return filas.stream().collect(Collectors.toMap(ConteoPorId::id, ConteoPorId::cantidad));
    }

    private static Map<String, Object> instantanea(ListaReferencia l) {
        Map<String, Object> v = new HashMap<>();
        v.put("nombre", l.getNombre());
        v.put("tipoDato", l.getTipoDato().name());
        v.put("ignoraMayusculas", l.isIgnoraMayusculas());
        v.put("elementos", l.elementosVigentes());
        return v;
    }

    public record DatosLista(String nombre, TipoDato tipoDato, boolean ignoraMayusculas, List<String> elementos) {
    }

    public record ListaConUso(ListaReferencia lista, long umbrales, long excepciones) {
    }
}
