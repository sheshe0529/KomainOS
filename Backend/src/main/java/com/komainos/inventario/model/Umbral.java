package com.komainos.inventario.model;

import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Criterio general con el que se evalúa una métrica (RF16, HU12, R2.4). En una métrica numérica define el operador y los
 * valores de advertencia y crítico; en una de lista, la lista de referencia y el nivel para los elementos faltantes y
 * sobrantes. ck_umbral_modalidad exige que cada umbral registre solo los campos de su modalidad.
 */
@Entity
@Table(name = "umbral")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Umbral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_umbral")
    private Integer id;

    /** La métrica define la modalidad: no cambia una vez creado el umbral */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_metrica", nullable = false, updatable = false)
    private Metrica metrica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lista_referencia")
    private ListaReferencia lista;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "operador", columnDefinition = "enum_operador_comparacion")
    private OperadorComparacion operador;

    @Column(name = "valor_advertencia", precision = 20, scale = 6)
    private BigDecimal valorAdvertencia;

    @Column(name = "valor_critico", precision = 20, scale = 6)
    private BigDecimal valorCritico;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_faltante", columnDefinition = "enum_nivel_alerta")
    private NivelAlerta nivelFaltante;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_sobrante", columnDefinition = "enum_nivel_alerta")
    private NivelAlerta nivelSobrante;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @OneToMany(mappedBy = "umbral", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<UmbralExcepcion> excepciones = new ArrayList<>();

    public static Umbral numerico(Metrica metrica, String nombre, LimitesNumericos limites, Instant ahora) {
        Umbral umbral = nuevo(metrica, nombre, ahora);
        umbral.actualizarNumerico(nombre, limites, ahora);
        return umbral;
    }

    public static Umbral deLista(Metrica metrica, String nombre, CriterioLista criterio, Instant ahora) {
        Umbral umbral = nuevo(metrica, nombre, ahora);
        umbral.actualizarLista(nombre, criterio, ahora);
        return umbral;
    }

    public boolean esDeLista() {
        return metrica.isEsLista();
    }

    /** El operador no cambia mientras haya excepciones: sus valores se validaron con el operador vigente */
    public void actualizarNumerico(String nombre, LimitesNumericos limites, Instant ahora) {
        exigirModalidad(false);
        if (limites.operador() == null) {
            throw new ReglaNegocioException("Indique el operador de comparación del umbral");
        }
        if (operador != null && operador != limites.operador() && !excepciones.isEmpty()) {
            throw new ReglaNegocioException(("El umbral %s tiene %d excepción(es): elimínelas antes de cambiar su "
                    + "operador").formatted(this.nombre, excepciones.size()));
        }
        validarNumericos(limites.operador(), limites.advertencia(), limites.critico());
        this.nombre = nombre;
        this.operador = limites.operador();
        this.valorAdvertencia = limites.advertencia();
        this.valorCritico = limites.critico();
        this.fechaActualizacion = ahora;
    }

    public void actualizarLista(String nombre, CriterioLista criterio, Instant ahora) {
        exigirModalidad(true);
        validarLista(criterio.lista(), lista);
        for (NivelAlerta nivel : new NivelAlerta[]{criterio.faltante(), criterio.sobrante()}) {
            if (nivel == NivelAlerta.NORMAL) {
                throw new ReglaNegocioException("El nivel para faltantes o sobrantes debe ser advertencia o crítico");
            }
        }
        if (criterio.faltante() == null && criterio.sobrante() == null) {
            throw new ReglaNegocioException("Indique el nivel para los elementos faltantes, para los sobrantes o ambos");
        }
        this.nombre = nombre;
        this.lista = criterio.lista();
        this.nivelFaltante = criterio.faltante();
        this.nivelSobrante = criterio.sobrante();
        this.fechaActualizacion = ahora;
    }

    /** Una sola excepción por alcance (uq_umbral_excepcion_alcance) */
    public UmbralExcepcion agregarExcepcion(ObjetivoExcepcion objetivo, ValoresExcepcion valores, String motivo,
                                           Instant ahora) {
        boolean repetida = excepciones.stream().anyMatch(e -> e.alcance() == objetivo.alcance()
                && Objects.equals(e.idObjetivo(), objetivo.idObjetivo()));
        if (repetida) {
            throw new ConflictoException("El umbral %s ya tiene una excepción para %s %s"
                    .formatted(nombre, objetivo.alcance().descripcion(), objetivo.nombre()));
        }
        validarValores(valores, null);
        UmbralExcepcion excepcion = UmbralExcepcion.nueva(this, objetivo, valores, motivo, ahora);
        excepciones.add(excepcion);
        return excepcion;
    }

    public void actualizarExcepcion(UmbralExcepcion excepcion, ValoresExcepcion valores, String motivo, Instant ahora) {
        validarValores(valores, excepcion.getLista());
        excepcion.aplicar(valores, motivo, ahora);
    }

    public void quitarExcepcion(UmbralExcepcion excepcion) {
        excepciones.remove(excepcion);
    }

    public Optional<UmbralExcepcion> excepcion(Integer idExcepcion) {
        return excepciones.stream().filter(e -> e.getId().equals(idExcepcion)).findFirst();
    }

    public void activar(Instant ahora) {
        if (activo) {
            throw new ReglaNegocioException("El umbral %s ya se encuentra activo".formatted(nombre));
        }
        activo = true;
        fechaActualizacion = ahora;
    }

    public void desactivar(Instant ahora) {
        if (!activo) {
            throw new ReglaNegocioException("El umbral %s ya se encuentra inactivo".formatted(nombre));
        }
        activo = false;
        fechaActualizacion = ahora;
    }

    private static Umbral nuevo(Metrica metrica, String nombre, Instant ahora) {
        if (!metrica.isActivo()) {
            throw new ReglaNegocioException("La métrica %s está inactiva".formatted(metrica.getNombre()));
        }
        Umbral umbral = new Umbral();
        umbral.metrica = metrica;
        umbral.nombre = nombre;
        umbral.activo = true;
        umbral.fechaCreacion = ahora;
        return umbral;
    }

    /**
     * Una excepción reemplaza los valores del umbral para su alcance (R2.4): en uno numérico, la advertencia y el
     * crítico con el mismo operador; en uno de lista, la lista. Un valor nulo es un nivel que no se evalúa.
     */
    private void validarValores(ValoresExcepcion valores, ListaReferencia listaAnterior) {
        if (esDeLista()) {
            if (valores.advertencia() != null || valores.critico() != null) {
                throw new ReglaNegocioException("La excepción de un umbral de lista solo cambia la lista");
            }
            validarLista(valores.lista(), listaAnterior);
            return;
        }
        if (valores.lista() != null) {
            throw new ReglaNegocioException("La excepción de un umbral numérico no lleva lista de referencia");
        }
        validarNumericos(operador, valores.advertencia(), valores.critico());
    }

    /**
     * Coherencia con el operador (HU12 CA2). Además de ck_umbral_coherencia, rechaza lo que dejaría un nivel sin efecto:
     * el crítico se evalúa primero, así que una advertencia igual al crítico nunca se informaría.
     */
    private void validarNumericos(OperadorComparacion operador, BigDecimal advertencia, BigDecimal critico) {
        if (advertencia == null && critico == null) {
            throw new ReglaNegocioException("Indique el valor de advertencia, el crítico o ambos");
        }
        metrica.getTipoDato().validarLimite(advertencia, "de advertencia");
        metrica.getTipoDato().validarLimite(critico, "crítico");
        if (metrica.getTipoDato() == TipoDato.BOOLEAN) {
            if (operador.ordena()) {
                throw new ReglaNegocioException("Una métrica booleana se compara con = o ≠: sus valores no tienen orden");
            }
            if (advertencia != null && critico != null) {
                throw new ReglaNegocioException(("Una métrica booleana alerta con un solo valor: indique la advertencia o "
                        + "el crítico, no ambos"));
            }
        }
        if (advertencia == null || critico == null) {
            return;
        }
        if (operador == OperadorComparacion.DISTINTO) {
            throw new ReglaNegocioException(("Con el operador ≠ indique un solo valor: con dos distintos toda medición "
                    + "alertaría, y con dos iguales la advertencia nunca se informaría"));
        }
        if (advertencia.compareTo(critico) == 0) {
            throw new ReglaNegocioException(("La advertencia y el crítico no pueden ser iguales: el crítico se evalúa "
                    + "primero y la advertencia nunca se informaría. Para un solo nivel, deje el otro vacío"));
        }
        if (!operador.esCoherente(advertencia, critico)) {
            throw new ReglaNegocioException(("Los valores no son coherentes con el operador %s: el crítico debe ser %s "
                    + "que la advertencia").formatted(operador.simbolo(),
                    operador == OperadorComparacion.MAYOR || operador == OperadorComparacion.MAYOR_IGUAL
                            ? "mayor" : "menor"));
        }
    }

    /** La lista debe ser del mismo tipo que la métrica evaluada (R2.4); una inactiva solo se conserva, no se asigna */
    private void validarLista(ListaReferencia candidata, ListaReferencia actual) {
        if (candidata == null) {
            throw new ReglaNegocioException("Indique la lista de referencia");
        }
        if (candidata.getTipoDato() != metrica.getTipoDato()) {
            throw new ReglaNegocioException("La lista %s es de tipo %s y la métrica %s es de tipo %s".formatted(
                    candidata.getNombre(), candidata.getTipoDato().etiqueta().toLowerCase(), metrica.getNombre(),
                    metrica.getTipoDato().etiqueta().toLowerCase()));
        }
        if (!candidata.isActivo() && candidata != actual) {
            throw new ReglaNegocioException("La lista %s está inactiva".formatted(candidata.getNombre()));
        }
    }

    private void exigirModalidad(boolean deLista) {
        if (esDeLista() != deLista) {
            throw new ReglaNegocioException(deLista
                    ? "La métrica %s es numérica: el umbral se define con operador y valores".formatted(metrica.getNombre())
                    : "La métrica %s es de lista: el umbral se define con una lista de referencia".formatted(metrica.getNombre()));
        }
    }

    public record LimitesNumericos(OperadorComparacion operador, BigDecimal advertencia, BigDecimal critico) {
    }

    /** faltante y sobrante: nivel que se asigna si falta o sobra algún elemento; nulo, no se evalúa */
    public record CriterioLista(ListaReferencia lista, NivelAlerta faltante, NivelAlerta sobrante) {
    }

    public record ValoresExcepcion(BigDecimal advertencia, BigDecimal critico, ListaReferencia lista) {
    }
}
