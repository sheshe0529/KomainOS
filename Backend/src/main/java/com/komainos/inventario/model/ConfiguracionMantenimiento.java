package com.komainos.inventario.model;

import com.komainos.shared.util.Tiempo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

/** Los cambios solo afectan a las órdenes nuevas */
@Entity
@Table(name = "configuracion_mantenimiento")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ConfiguracionMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_configuracion_mantenimiento")
    private Integer id;

    @Column(name = "id_cuenta_servicio")
    private Integer idCuentaServicio;

    @Column(name = "frecuencia_revision_dias", nullable = false)
    private Integer frecuenciaRevisionDias;

    @Column(name = "frecuencia_mantenimiento_dias", nullable = false)
    private Integer frecuenciaMantenimientoDias;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "modalidad_planificacion", nullable = false, columnDefinition = "enum_modalidad_planificacion")
    private ModalidadPlanificacion modalidadPlanificacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    protected void aplicarComunes(int frecuenciaRevisionDias, int frecuenciaMantenimientoDias,
                                  ModalidadPlanificacion modalidad) {
        this.frecuenciaRevisionDias = frecuenciaRevisionDias;
        this.frecuenciaMantenimientoDias = frecuenciaMantenimientoDias;
        this.modalidadPlanificacion = modalidad;
    }

    public boolean esAutomatica() {
        return modalidadPlanificacion == ModalidadPlanificacion.AUTOMATICA;
    }

    @PrePersist
    void alInsertar() {
        Instant ahora = Tiempo.ahora();
        fechaCreacion = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = Tiempo.ahora();
    }
}
