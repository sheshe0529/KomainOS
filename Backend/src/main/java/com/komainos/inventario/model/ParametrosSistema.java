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

/**
 * Parametros globales del sistema (RF68, RF65, RF02), tabla
 * {@code configuracion_sistema}.
 *
 * <p>Es un registro unico: la base obliga a que su id sea siempre 1
 * (ck_configuracion_sistema_unico). La planificacion lee de aqui la
 * concurrencia maxima y la duracion maxima por MOP (DEC-08).
 */
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

    /** Cuenta de servicio predeterminada (RF06). Se gestiona con credenciales. */
    @Column(name = "id_cuenta_servicio_predeterminada")
    private Integer idCuentaServicioPredeterminada;

    /** Servidores en ejecucion simultanea como maximo (RF68). */
    @Column(name = "max_ejecuciones_concurrentes", nullable = false)
    private Integer maxEjecucionesConcurrentes;

    /** Duracion maxima por tarea, en minutos (RF22, RF68). */
    @Column(name = "max_duracion_tarea_minutos", nullable = false)
    private Integer maxDuracionTareaMinutos;

    /** Duracion maxima por MOP, en minutos (RF60, RF68). */
    @Column(name = "max_duracion_mop_minutos", nullable = false)
    private Integer maxDuracionMopMinutos;

    /** Mantenimientos consecutivos conformes para la racha estable (RF65). */
    @Column(name = "min_ciclos_racha_estable", nullable = false)
    private Integer minCiclosRachaEstable;

    /** Vigencia del token de sesion (RF02). */
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
