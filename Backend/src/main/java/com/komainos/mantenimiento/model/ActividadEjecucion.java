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
 * <p>
 * Cada evento tiene un solo origen y el servidor sale de él (DEC-60): la evaluación, la tarea de la MOP o, para los
 * pasos generales de la ejecución en un servidor, el detalle de la orden.
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

    /** Un paso general de la ejecución en el servidor: inicio, comprobación de acceso y cierre */
    @Column(name = "id_orden_detalle")
    private Integer idDetalle;

    /** Un paso de un análisis: evaluación previa, análisis posterior o revisión periódica */
    @Column(name = "id_evaluacion")
    private Integer idEvaluacion;

    /** Un paso de una tarea de la MOP */
    @Column(name = "id_mop_tarea")
    private Integer idTarea;

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

    public static ActividadEjecucion deEvaluacion(Integer idEvaluacion, Paso paso) {
        return nuevo(null, idEvaluacion, null, paso);
    }

    public static ActividadEjecucion deTarea(Integer idTarea, Paso paso) {
        return nuevo(null, null, idTarea, paso);
    }

    public static ActividadEjecucion deDetalle(Integer idDetalle, Paso paso) {
        return nuevo(idDetalle, null, null, paso);
    }

    private static ActividadEjecucion nuevo(Integer idDetalle, Integer idEvaluacion, Integer idTarea, Paso paso) {
        ActividadEjecucion a = new ActividadEjecucion();
        a.idDetalle = idDetalle;
        a.idEvaluacion = idEvaluacion;
        a.idTarea = idTarea;
        a.nivel = paso.nivel();
        a.estado = paso.estado();
        a.accion = recortar(paso.accion(), 300);
        a.detalle = paso.detalle();
        a.error = paso.error();
        a.script = recortar(paso.script(), 300);
        a.fechaHora = paso.fechaHora();
        return a;
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }

    /** Lo que se registra de un paso, sea cual sea su origen */
    public record Paso(NivelActividad nivel, EstadoActividad estado, String accion, String detalle, String error,
                       String script, Instant fechaHora) {
    }
}
