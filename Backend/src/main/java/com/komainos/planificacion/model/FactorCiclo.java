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

/**
 * Factor que ajusta la periodicidad base segun el resultado del ciclo (RF64),
 * tabla {@code factor_ciclo}. Se siembra en V2 con 0,50 / 0,75 / 1,00 / 1,25.
 */
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
