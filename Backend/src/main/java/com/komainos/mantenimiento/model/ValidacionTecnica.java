package com.komainos.mantenimiento.model;

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

import java.time.Instant;

/** Validación técnica automática de un servidor tras ejecutar su MOP (RF55), una por detalle */
@Entity
@Table(name = "validacion_tecnica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidacionTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_validacion_tecnica")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden_detalle", nullable = false)
    private OrdenDetalle detalle;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "resultado", nullable = false, columnDefinition = "enum_resultado_validacion_tecnica")
    private ResultadoValidacionTecnica resultado;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "fecha_validacion", nullable = false)
    private Instant fechaValidacion;

    public static ValidacionTecnica registrar(OrdenDetalle detalle, boolean conforme, String observaciones,
                                              Instant ahora) {
        ValidacionTecnica v = new ValidacionTecnica();
        v.detalle = detalle;
        v.resultado = conforme ? ResultadoValidacionTecnica.CONFORME : ResultadoValidacionTecnica.NO_CONFORME;
        v.observaciones = observaciones;
        v.fechaValidacion = ahora;
        return v;
    }

    public boolean esConforme() {
        return resultado == ResultadoValidacionTecnica.CONFORME;
    }
}
