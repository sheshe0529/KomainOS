package com.komainos.planificacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;

/** Ajusta la periodicidad base según el resultado del ciclo (RF64) */
@Entity
@Table(name = "factor_ciclo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FactorCiclo {

    @Id
    @Column(name = "id_factor_ciclo")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "resultado", nullable = false, columnDefinition = "enum_resultado_ciclo")
    private ResultadoCiclo resultado;

    @Column(name = "factor", nullable = false, precision = 10, scale = 4)
    private BigDecimal factor;
}
