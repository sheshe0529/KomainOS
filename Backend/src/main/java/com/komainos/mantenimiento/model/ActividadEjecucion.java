package com.komainos.mantenimiento.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

/**
 * Un paso de una ejecución remota con su hora exacta: conexión, comprobación de acceso, inicio y fin de un análisis o de
 * una tarea (DEC-59). Es un registro: no se modifica. Las referencias son identificadores, sin asociaciones, porque se
 * escribe en su propia transacción mientras la ejecución avanza.
 */
@Entity
@Table(name = "actividad_ejecucion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActividadEjecucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad_ejecucion")
    private Integer id;

    @Column(name = "id_servidor", nullable = false)
    private Integer idServidor;

    /** El servidor dentro de una orden; nulo en una revisión periódica */
    @Column(name = "id_orden_detalle")
    private Integer idDetalle;

    @Column(name = "id_evaluacion")
    private Integer idEvaluacion;

    @Column(name = "id_mop_tarea")
    private Integer idTarea;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "fase", nullable = false, columnDefinition = "enum_fase_actividad")
    private FaseActividad fase;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel", nullable = false, columnDefinition = "enum_nivel_actividad")
    private NivelActividad nivel;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", columnDefinition = "enum_estado_actividad")
    private EstadoActividad estado;

    @Column(name = "accion", nullable = false, length = 300)
    private String accion;

    @Column(name = "detalle")
    private String detalle;

    /** Con la categoría al principio («Error de autenticación: …»), sin secretos */
    @Column(name = "error")
    private String error;

    /** Script y versión, por ejemplo «Análisis básico de salud v3» */
    @Column(name = "script", length = 300)
    private String script;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    public static ActividadEjecucion de(Integer idServidor, Integer idDetalle, Integer idEvaluacion, Integer idTarea,
                                        FaseActividad fase, NivelActividad nivel, EstadoActividad estado,
                                        String accion, String detalle, String error, String script, Instant ahora) {
        ActividadEjecucion a = new ActividadEjecucion();
        a.idServidor = idServidor;
        a.idDetalle = idDetalle;
        a.idEvaluacion = idEvaluacion;
        a.idTarea = idTarea;
        a.fase = fase;
        a.nivel = nivel;
        a.estado = estado;
        a.accion = recortar(accion, 300);
        a.detalle = detalle;
        a.error = error;
        a.script = recortar(script, 300);
        a.fechaHora = ahora;
        return a;
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }
}
