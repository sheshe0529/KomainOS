package com.komainos.inventario.model;

import com.komainos.shared.util.Tiempo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Registro único: la base obliga a que su id sea siempre 1 */
@Entity
@Table(name = "configuracion_sistema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParametrosSistema {

    public static final int ID_UNICO = 1;

    @Id
    @Column(name = "id_configuracion_sistema")
    private Integer id;

    @Column(name = "id_cuenta_servicio_predeterminada")
    private Integer idCuentaServicioPredeterminada;

    @Column(name = "max_ejecuciones_concurrentes", nullable = false)
    private Integer maxEjecucionesConcurrentes;

    @Column(name = "max_duracion_tarea_minutos", nullable = false)
    private Integer maxDuracionTareaMinutos;

    @Column(name = "max_duracion_mop_minutos", nullable = false)
    private Integer maxDuracionMopMinutos;

    @Column(name = "min_ciclos_racha_estable", nullable = false)
    private Integer minCiclosRachaEstable;

    @Column(name = "minutos_expiracion_token", nullable = false)
    private Integer minutosExpiracionToken;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @PrePersist
    @PreUpdate
    void alGuardar() {
        fechaActualizacion = Tiempo.ahora();
    }
}
