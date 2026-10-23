package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Conjunto esperado o permitido con el que se comparan los elementos que informa una métrica de lista (R2.4). Su tipo
 * de dato no cambia: los umbrales que la usan dependen de que coincida con el de su métrica.
 */
@Entity
@Table(name = "lista_referencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListaReferencia {

    public static final int MAXIMO_ELEMENTOS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lista_referencia")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_dato", nullable = false, updatable = false, columnDefinition = "enum_tipo_dato_parametro")
    private TipoDato tipoDato;

    /** Solo en listas de texto: los elementos se guardan en minúsculas y se comparan así */
    @Column(name = "ignora_mayusculas", nullable = false)
    private boolean ignoraMayusculas;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /** Incluye los retirados: son el historial que explica las evaluaciones pasadas */
    @OneToMany(mappedBy = "lista", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ListaReferenciaItem> items = new ArrayList<>();

    /** Sin elementos: el servicio los agrega con reemplazarElementos una vez que la lista tiene id */
    public static ListaReferencia nueva(String nombre, TipoDato tipoDato, boolean ignoraMayusculas, Instant ahora) {
        if (!tipoDato.admiteLista()) {
            throw new ReglaNegocioException("Las listas de referencia no admiten elementos de tipo %s"
                    .formatted(tipoDato.etiqueta().toLowerCase()));
        }
        ListaReferencia lista = new ListaReferencia();
        lista.nombre = nombre;
        lista.tipoDato = tipoDato;
        lista.ignoraMayusculas = tipoDato == TipoDato.STRING && ignoraMayusculas;
        lista.activo = true;
        lista.fechaActualizacion = ahora;
        return lista;
    }

    public void renombrar(String nombre, Instant ahora) {
        this.nombre = nombre;
        this.fechaActualizacion = ahora;
    }

    /** Cambiarla vuelve a canonizar los elementos al reemplazarlos: llamar después a reemplazarElementos */
    public void definirIgnoraMayusculas(boolean ignora) {
        this.ignoraMayusculas = tipoDato == TipoDato.STRING && ignora;
    }

    /** Forma en que se guarda y se compara un elemento de esta lista */
    public String canonico(String crudo) {
        return tipoDato.canonico(crudo, ignoraMayusculas);
    }

    public List<String> elementosVigentes() {
        return ordenados(ListaReferenciaItem::estaVigente);
    }

    /** Contenido que tenía la lista en ese instante: con él se explica una evaluación pasada (evaluacion_umbral.fecha_evaluacion) */
    public List<String> elementosEn(Instant momento) {
        return ordenados(i -> i.vigenteEn(momento));
    }

    /**
     * Deja vigentes exactamente esos elementos: retira los que sobran y da de alta los que faltan, sin tocar los que se
     * mantienen. Las marcas de un mismo valor son estrictamente crecientes, así la vigencia no se superpone aunque el
     * reloj no haya avanzado (y la fecha de alta, que es parte de la clave, nunca se repite). Las fechas de los
     * elementos van en microsegundos, como lista_referencia_item.
     */
    public CambioElementos reemplazarElementos(Collection<String> crudos, Instant ahora) {
        Set<String> nuevos = new LinkedHashSet<>();
        for (String crudo : crudos) {
            nuevos.add(canonico(crudo));
        }
        if (nuevos.isEmpty()) {
            throw new ReglaNegocioException("La lista debe tener al menos un elemento");
        }
        if (nuevos.size() > MAXIMO_ELEMENTOS) {
            throw new ReglaNegocioException("La lista admite hasta %d elementos".formatted(MAXIMO_ELEMENTOS));
        }
        Instant momento = ahora.truncatedTo(ChronoUnit.MICROS);
        List<String> retirados = new ArrayList<>();
        for (ListaReferenciaItem item : items) {
            if (item.estaVigente() && !nuevos.contains(item.getValor())) {
                item.retirar(posterior(momento, item.getFechaAlta()));
                retirados.add(item.getValor());
            }
        }
        List<String> vigentes = elementosVigentes();
        List<String> agregados = new ArrayList<>();
        for (String valor : nuevos) {
            if (!vigentes.contains(valor)) {
                items.add(ListaReferenciaItem.nuevo(this, valor, altaPara(valor, momento)));
                agregados.add(valor);
            }
        }
        if (!retirados.isEmpty() || !agregados.isEmpty()) {
            fechaActualizacion = ahora.truncatedTo(ChronoUnit.SECONDS);
        }
        agregados.sort(tipoDato.orden());
        retirados.sort(tipoDato.orden());
        return new CambioElementos(agregados, retirados);
    }

    public void activar() {
        if (activo) {
            throw new ReglaNegocioException("La lista %s ya se encuentra activa".formatted(nombre));
        }
        activo = true;
    }

    public void desactivar() {
        if (!activo) {
            throw new ReglaNegocioException("La lista %s ya se encuentra inactiva".formatted(nombre));
        }
        activo = false;
    }

    private List<String> ordenados(Predicate<ListaReferenciaItem> filtro) {
        return items.stream().filter(filtro).map(ListaReferenciaItem::getValor).sorted(tipoDato.orden()).toList();
    }

    /** No antes de la última baja del mismo valor: el periodo nuevo empieza donde terminó el anterior, o después */
    private Instant altaPara(String valor, Instant momento) {
        Instant alta = momento;
        for (ListaReferenciaItem item : items) {
            if (item.getValor().equals(valor) && item.getFechaBaja() != null && item.getFechaBaja().isAfter(alta)) {
                alta = item.getFechaBaja();
            }
        }
        return alta;
    }

    /** La baja es estrictamente posterior al alta aunque el reloj no haya avanzado */
    private static Instant posterior(Instant momento, Instant alta) {
        return momento.isAfter(alta) ? momento : alta.plus(1, ChronoUnit.MICROS);
    }

    public record CambioElementos(List<String> agregados, List<String> retirados) {

        public boolean hayCambios() {
            return !agregados.isEmpty() || !retirados.isEmpty();
        }
    }
}
