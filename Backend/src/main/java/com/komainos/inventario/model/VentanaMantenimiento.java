package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;
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
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Duration;
import java.time.LocalTime;

/** Si diaFin difiere de diaInicio el intervalo cruza la medianoche (hora de la zona operativa, DEC-06) */
@Entity
@Table(name = "ventana_mantenimiento")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VentanaMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ventana_mantenimiento")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false)
    private Servidor servidor;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "dia_inicio", nullable = false, columnDefinition = "enum_dia_semana")
    private DiaSemana diaInicio;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "dia_fin", nullable = false, columnDefinition = "enum_dia_semana")
    private DiaSemana diaFin;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /** Valida la regla de ck_ventana_intervalo para responder con un mensaje claro */
    public static VentanaMantenimiento nueva(DiaSemana diaInicio, LocalTime horaInicio,
                                             DiaSemana diaFin, LocalTime horaFin) {
        if (diaInicio == diaFin && !horaFin.isAfter(horaInicio)) {
            throw new ReglaNegocioException(
                    "En una ventana del mismo día la hora de fin debe ser posterior a la de inicio (%s %s–%s)"
                            .formatted(diaInicio, horaInicio, horaFin));
        }
        VentanaMantenimiento ventana = new VentanaMantenimiento();
        ventana.diaInicio = diaInicio;
        ventana.horaInicio = horaInicio.withSecond(0).withNano(0);
        ventana.diaFin = diaFin;
        ventana.horaFin = horaFin.withSecond(0).withNano(0);
        return ventana;
    }

    public Duration duracion() {
        long minutos = diaInicio.diasHasta(diaFin) * 24L * 60
                + Duration.between(horaInicio, horaFin).toMinutes();
        return Duration.ofMinutes(minutos);
    }
}
