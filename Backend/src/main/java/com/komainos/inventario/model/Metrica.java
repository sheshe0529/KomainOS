package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Métrica que informa el script de análisis, identificada por su código (RF16, R2.4). tipo_dato es el tipo literal y
 * es_lista distingue una medición escalar de un conjunto de elementos: los umbrales consultan esta definición. Cada
 * variable del R3.1 indica en qué sistemas operativos se mide: en los demás, el análisis no la informa ni se exige.
 */
@Entity
@Table(name = "metrica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Metrica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metrica")
    private Integer id;

    /** Lo que busca el sistema en la salida del script: no cambia una vez registrado */
    @Column(name = "codigo", nullable = false, updatable = false, length = 100)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    /** Qué mide y cómo se interpreta el valor (R3.1) */
    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "unidad", length = 100)
    private String unidad;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_dato", nullable = false, columnDefinition = "enum_tipo_dato_parametro")
    private TipoDato tipoDato;

    @Column(name = "es_lista", nullable = false)
    private boolean esLista;

    /** Familia en la que el análisis informa la métrica; sin valor, en Linux y en Windows (R3.1) */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "so_aplica", columnDefinition = "enum_familia_so")
    private FamiliaSistemaOperativo soAplica;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /** Una métrica que se mide en Linux y en Windows, sin descripción */
    public static Metrica nueva(String codigo, String nombre, String unidad, TipoDato tipoDato, boolean esLista) {
        return nueva(codigo, new Definicion(nombre, null, unidad, tipoDato, esLista, null));
    }

    public static Metrica nueva(String codigo, Definicion definicion) {
        Metrica metrica = new Metrica();
        metrica.codigo = codigo;
        metrica.activo = true;
        metrica.actualizar(definicion);
        return metrica;
    }

    /** El servicio impide cambiar el tipo cuando ya hay umbrales o mediciones que dependen de él */
    public void actualizar(Definicion d) {
        validarTipo(d.tipoDato(), d.esLista());
        validarUnidad(d.unidad(), d.tipoDato(), d.esLista());
        this.nombre = d.nombre();
        this.descripcion = d.descripcion();
        this.unidad = d.unidad();
        this.tipoDato = d.tipoDato();
        this.esLista = d.esLista();
        this.soAplica = d.soAplica();
    }

    /** Si el script de análisis de esa familia la informa: en las demás no se exige ni se evalúa */
    public boolean aplicaA(FamiliaSistemaOperativo familia) {
        return familia == null || soAplica == null || soAplica == familia;
    }

    /** «Linux», «Windows» o «Linux y Windows», para los mensajes */
    public String sistemasOperativos() {
        return soAplica == null ? "Linux y Windows" : soAplica == FamiliaSistemaOperativo.LINUX ? "Linux" : "Windows";
    }

    /** Las familias en las que se mide una métrica con ese so_aplica */
    public static Set<FamiliaSistemaOperativo> familias(FamiliaSistemaOperativo soAplica) {
        return soAplica == null ? EnumSet.allOf(FamiliaSistemaOperativo.class) : EnumSet.of(soAplica);
    }

    public boolean cambiaTipo(TipoDato tipoDato, boolean esLista) {
        return this.tipoDato != tipoDato || this.esLista != esLista;
    }

    public void activar() {
        if (activo) {
            throw new ReglaNegocioException("La métrica %s ya se encuentra activa".formatted(nombre));
        }
        activo = true;
    }

    public void desactivar() {
        if (!activo) {
            throw new ReglaNegocioException("La métrica %s ya se encuentra inactiva".formatted(nombre));
        }
        activo = false;
    }

    /** La unidad describe una magnitud: un conjunto de elementos o un verdadero/falso no la tienen */
    private static void validarUnidad(String unidad, TipoDato tipoDato, boolean esLista) {
        if (unidad == null) {
            return;
        }
        if (esLista) {
            throw new ReglaNegocioException("Una métrica de lista no lleva unidad: se comparan sus elementos, no una magnitud");
        }
        if (tipoDato == TipoDato.BOOLEAN) {
            throw new ReglaNegocioException("Una métrica booleana no lleva unidad: su valor es 0 (falso) o 1 (verdadero)");
        }
    }

    /**
     * Lo que se puede cambiar de una métrica: todo menos el código, que es lo que busca el sistema en la salida
     *
     * @param soAplica la familia en la que se mide; nula si se mide en Linux y en Windows
     */
    public record Definicion(String nombre, String descripcion, String unidad, TipoDato tipoDato, boolean esLista,
                             FamiliaSistemaOperativo soAplica) {
    }

    /** medicion_metrica.valor es numérico, y una lista de verdadero y falso no tiene sentido */
    private static void validarTipo(TipoDato tipoDato, boolean esLista) {
        if (esLista && !tipoDato.admiteLista()) {
            throw new ReglaNegocioException("Una métrica de lista no puede ser de tipo %s"
                    .formatted(tipoDato.etiqueta().toLowerCase()));
        }
        if (!esLista && !tipoDato.admiteMetricaEscalar()) {
            throw new ReglaNegocioException("Una métrica de valor único debe ser entera, decimal o booleana");
        }
    }
}
