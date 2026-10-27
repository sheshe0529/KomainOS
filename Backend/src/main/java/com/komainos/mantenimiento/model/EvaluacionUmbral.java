package com.komainos.mantenimiento.model;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.UmbralAplicado;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado de comparar una medición con un umbral, con los valores que rigieron en ese momento (DEC-41): si después
 * cambia el umbral o su excepción, la evaluación histórica sigue mostrando contra qué se comparó.
 */
@Entity
@Table(name = "evaluacion_umbral")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluacionUmbral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluacion_umbral")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medicion_metrica", nullable = false)
    private MedicionMetrica medicion;

    @Column(name = "id_umbral", nullable = false)
    private Integer idUmbral;

    @Column(name = "id_umbral_excepcion")
    private Integer idExcepcion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "operador_aplicado", columnDefinition = "enum_operador_comparacion")
    private OperadorComparacion operador;

    @Column(name = "valor_advertencia_aplicado")
    private BigDecimal advertencia;

    @Column(name = "valor_critico_aplicado")
    private BigDecimal critico;

    @Column(name = "id_lista_referencia_aplicada")
    private Integer idLista;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_faltante_aplicado", columnDefinition = "enum_nivel_alerta")
    private NivelAlerta nivelFaltante;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_sobrante_aplicado", columnDefinition = "enum_nivel_alerta")
    private NivelAlerta nivelSobrante;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel", nullable = false, columnDefinition = "enum_nivel_alerta")
    private NivelAlerta nivel;

    @Column(name = "fecha_evaluacion", nullable = false)
    private Instant fechaEvaluacion;

    public static EvaluacionUmbral de(MedicionMetrica medicion, UmbralAplicado aplicado, NivelAlerta nivel,
                                      Instant ahora) {
        EvaluacionUmbral e = new EvaluacionUmbral();
        e.medicion = medicion;
        e.idUmbral = aplicado.umbral().getId();
        e.idExcepcion = aplicado.excepcion() == null ? null : aplicado.excepcion().getId();
        e.operador = aplicado.operador();
        e.advertencia = aplicado.advertencia();
        e.critico = aplicado.critico();
        e.idLista = aplicado.lista() == null ? null : aplicado.lista().getId();
        e.nivelFaltante = aplicado.nivelFaltante();
        e.nivelSobrante = aplicado.nivelSobrante();
        e.nivel = nivel;
        e.fechaEvaluacion = ahora;
        return e;
    }
}
