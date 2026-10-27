package com.komainos.mantenimiento.model;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.VersionScript;
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
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

/**
 * Tarea de una MOP con lo que se fijó al generarla (RF41, RF42): prioridad, duración, afectación, versión del script y
 * cuenta. Cambiar después la tarea o publicar otra versión no altera lo fijado. Registra el resultado de su ejecución,
 * ya sin secretos (RF53, RNF11).
 */
@Entity
@Table(name = "mop_tarea")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MopTarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mop_tarea")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_mop", nullable = false)
    private Mop mop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tarea", nullable = false)
    private Tarea tarea;

    /** Nula si no hay implementación compatible: la orden queda sin implementación (RF42) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_script_version")
    private VersionScript version;

    @Column(name = "id_cuenta_servicio")
    private Integer idCuentaServicio;

    @Column(name = "orden_ejecucion", nullable = false)
    private Integer ordenEjecucion;

    @Column(name = "prioridad_aplicada", nullable = false)
    private Integer prioridadAplicada;

    @Column(name = "duracion_estimada_minutos", nullable = false)
    private Integer duracionEstimadaMinutos;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_afectacion_aplicado", nullable = false, columnDefinition = "enum_nivel_afectacion")
    private NivelAfectacion nivelAfectacion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_tarea_mop")
    private EstadoTareaMop estado;

    @Column(name = "codigo_retorno")
    private Integer codigoRetorno;

    @Column(name = "salida")
    private String salida;

    @Column(name = "error")
    private String error;

    @Column(name = "fecha_inicio")
    private Instant fechaInicio;

    @Column(name = "fecha_fin")
    private Instant fechaFin;

    static MopTarea fijar(Mop mop, Tarea tarea, VersionScript version, Integer idCuentaServicio, int ordenEjecucion) {
        MopTarea t = new MopTarea();
        t.mop = mop;
        t.tarea = tarea;
        t.version = version;
        t.idCuentaServicio = idCuentaServicio;
        t.ordenEjecucion = ordenEjecucion;
        t.prioridadAplicada = tarea.getPrioridad();
        t.duracionEstimadaMinutos = tarea.getDuracionEstimadaMinutos();
        t.nivelAfectacion = tarea.getNivelAfectacion();
        t.estado = EstadoTareaMop.PENDIENTE;
        return t;
    }

    public void iniciar(Instant ahora) {
        cambiar(EstadoTareaMop.EN_EJECUCION);
        fechaInicio = ahora;
    }

    /**
     * @param codigo nulo si el script no llegó a ejecutarse en el servidor
     */
    public void terminar(boolean correcta, Integer codigo, String salida, String error, Instant ahora) {
        cambiar(correcta ? EstadoTareaMop.FINALIZADA : EstadoTareaMop.FALLIDA);
        this.codigoRetorno = codigo;
        this.salida = salida == null || salida.isBlank() ? null : salida;
        this.error = error;
        this.fechaFin = ahora;
    }

    /** RF59, RF60: las pendientes no se inician; la que está en curso termina por su cuenta */
    public void omitir() {
        if (estado == EstadoTareaMop.PENDIENTE) {
            estado = EstadoTareaMop.NO_INICIADA;
        }
    }

    /** Una tarea que estaba en curso cuando se detuvo el sistema no tiene resultado conocido */
    public void interrumpir(String motivo, Instant ahora) {
        if (estado == EstadoTareaMop.EN_EJECUCION) {
            terminar(false, null, null, motivo, ahora);
        }
    }

    private void cambiar(EstadoTareaMop destino) {
        if (!estado.puedePasarA(destino)) {
            throw new ReglaNegocioException("La tarea «%s» no puede pasar de %s a %s"
                    .formatted(tarea.getNombre(), estado, destino));
        }
        estado = destino;
    }
}
