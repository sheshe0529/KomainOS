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

/**
 * Métrica que informa el script de análisis, identificada por su código (RF16, R2.4). tipo_dato es el tipo literal y
 * es_lista distingue una medición escalar de un conjunto de elementos: los umbrales consultan esta definición.
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

    @Column(name = "unidad", length = 100)
    private String unidad;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_dato", nullable = false, columnDefinition = "enum_tipo_dato_parametro")
    private TipoDato tipoDato;

    @Column(name = "es_lista", nullable = false)
    private boolean esLista;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    public static Metrica nueva(String codigo, String nombre, String unidad, TipoDato tipoDato, boolean esLista) {
        Metrica metrica = new Metrica();
        metrica.codigo = codigo;
        metrica.activo = true;
        metrica.actualizar(nombre, unidad, tipoDato, esLista);
        return metrica;
    }

    /** El servicio impide cambiar el tipo cuando ya hay umbrales o mediciones que dependen de él */
    public void actualizar(String nombre, String unidad, TipoDato tipoDato, boolean esLista) {
        validarTipo(tipoDato, esLista);
        this.nombre = nombre;
        this.unidad = unidad;
        this.tipoDato = tipoDato;
        this.esLista = esLista;
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
