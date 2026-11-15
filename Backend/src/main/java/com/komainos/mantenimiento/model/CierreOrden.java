package com.komainos.mantenimiento.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_siguiente", unique = true)
    private Orden ordenSiguiente;

    @Column(name = "periodicidad_base_dias", nullable = false)
    private Integer periodicidadBaseDias;

    @Column(name = "fecha_cierre", nullable = false)
    private Instant fechaCierre;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    /** Conserva la periodicidad vigente al cierre para calcular la fecha objetivo del siguiente ciclo */
    public static CierreOrden nuevo(Orden orden, int periodicidad, Instant ahora, String observaciones) {
        CierreOrden cierre = new CierreOrden();
        cierre.orden = orden;
        cierre.periodicidadBaseDias = periodicidad;
        cierre.fechaCierre = ahora;
        cierre.observaciones = observaciones == null || observaciones.length() <= 1000
                ? observaciones : observaciones.substring(0, 999) + "…";
        return cierre;
    }

    public void enlazarSiguiente(Orden siguiente) {
        this.ordenSiguiente = siguiente;
    }
}
