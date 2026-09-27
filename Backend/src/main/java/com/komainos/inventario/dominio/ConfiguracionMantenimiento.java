package com.komainos.inventario.dominio;

import com.komainos.shared.dominio.Tiempo;
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

/**
 * Superclase de la generalizacion de configuraciones (RF17, RF70), tabla
 * {@code configuracion_mantenimiento}. Las especializaciones
 * {@link ConfiguracionServidor} y {@link ConfiguracionGrupo} comparten su
 * identificador, tal como lo modela R2.4.
 *
 * <p>Su ausencia representa un registro pendiente de configuracion (DEC-14).
 * Los cambios afectan solo a las ordenes nuevas (HU13 CA7): cada orden guarda
 * los valores que aplico al generarse.
 */
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

    /**
     * Cuenta de servicio propia. Nula significa usar la predeterminada del
     * sistema (R2.4). Se gestiona con el modulo de credenciales (DEC-16), por
     * eso se mapea como identificador y no como asociacion.
     */
    @Column(name = "id_cuenta_servicio")
    private Integer idCuentaServicio;

    @Column(name = "frecuencia_revision_dias", nullable = false)
    private Integer frecuenciaRevisionDias;

    /** Periodicidad base del mantenimiento (RF64). */
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
