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

/**
 * Cierre de una orden (RF63, RF64), tabla {@code cierre_orden}.
 *
 * <p>En esta iteracion solo lo lee la planificacion: el cierre lo registra el
 * ciclo de mantenimiento (iteracion posterior). La planificacion calcula la
 * fecha objetivo del ciclo siguiente con su periodicidad y factor, y enlaza la
 * orden generada en {@code id_orden_siguiente} (RF29).
 */
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

    /** RF29: enlaza la orden del ciclo siguiente generada por la planificacion. */
    public void enlazarSiguiente(Orden siguiente) {
        this.ordenSiguiente = siguiente;
    }
}
