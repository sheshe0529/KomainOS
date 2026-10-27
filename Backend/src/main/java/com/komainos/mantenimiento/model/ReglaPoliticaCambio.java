package com.komainos.mantenimiento.model;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.inventario.model.Entorno;
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

/**
 * Celda de la política de gestión del cambio (RF43): para un entorno y una afectación, qué aprobaciones se exigen. Sin
 * aprobadores, la autorización es automática. Es única por entorno y afectación (uq_regla_politica_cambio).
 */
@Entity
@Table(name = "regla_politica_cambio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReglaPoliticaCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regla_politica_cambio")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entorno", nullable = false)
    private Entorno entorno;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_afectacion", nullable = false, columnDefinition = "enum_nivel_afectacion")
    private NivelAfectacion afectacion;

    @Column(name = "requiere_aprobacion_responsable", nullable = false)
    private boolean requiereResponsable;

    @Column(name = "requiere_aprobacion_administrador", nullable = false)
    private boolean requiereAdministrador;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /** También sirve de versión para detectar ediciones concurrentes desde el panel */
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public static ReglaPoliticaCambio nueva(Entorno entorno, NivelAfectacion afectacion) {
        ReglaPoliticaCambio regla = new ReglaPoliticaCambio();
        regla.entorno = entorno;
        regla.afectacion = afectacion;
        return regla;
    }

    public void configurar(boolean requiereResponsable, boolean requiereAdministrador, boolean activo, Instant ahora) {
        this.requiereResponsable = requiereResponsable;
        this.requiereAdministrador = requiereAdministrador;
        this.activo = activo;
        this.fechaActualizacion = ahora;
    }

    public boolean esAutomatica() {
        return !requiereResponsable && !requiereAdministrador;
    }
}
