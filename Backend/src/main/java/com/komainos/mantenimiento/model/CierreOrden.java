package com.komainos.mantenimiento.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Cierre del ciclo (RF63): lo registra el ciclo de mantenimiento y la planificación lo usa para el siguiente (RF29) */
@Entity
@Table(name = "cierre_orden")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CierreOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cierre_orden")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false, unique = true)
    private Orden orden;

    @Column(name = "id_factor_ciclo", nullable = false)
    private Integer idFactorCiclo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_siguiente", unique = true)
    private Orden ordenSiguiente;

    @Column(name = "periodicidad_base_dias", nullable = false)
    private Integer periodicidadBaseDias;

    @Column(name = "factor_aplicado", nullable = false, precision = 10, scale = 4)
    private BigDecimal factorAplicado;

    @Column(name = "fecha_cierre", nullable = false)
    private Instant fechaCierre;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    /** RF63, RF64: conserva la periodicidad y el factor con que se calculará la fecha objetivo del siguiente ciclo */
    public static CierreOrden nuevo(Orden orden, Integer idFactor, int periodicidad, BigDecimal factor, Instant ahora,
                                    String observaciones) {
        CierreOrden cierre = new CierreOrden();
        cierre.orden = orden;
        cierre.idFactorCiclo = idFactor;
        cierre.periodicidadBaseDias = periodicidad;
        cierre.factorAplicado = factor;
        cierre.fechaCierre = ahora;
        cierre.observaciones = observaciones == null || observaciones.length() <= 1000
                ? observaciones : observaciones.substring(0, 999) + "…";
        return cierre;
    }

    public void enlazarSiguiente(Orden siguiente) {
        this.ordenSiguiente = siguiente;
    }
}
