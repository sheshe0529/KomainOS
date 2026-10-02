package com.komainos.mantenimiento.model;

import com.komainos.seguridad.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/** Cada reprogramación crea una versión nueva y las anteriores quedan como historial (RF30) */
@Entity
@Table(name = "programacion_orden")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramacionOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_programacion_orden")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_registro")
    private Usuario usuarioRegistro;

    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion;

    /** Calculada con periodicidad y factor, antes de aplicar restricciones (RF64) */
    @Column(name = "fecha_objetivo", nullable = false)
    private Instant fechaObjetivo;

    @Column(name = "fecha_inicio_programada", nullable = false)
    private Instant fechaInicioProgramada;

    @Column(name = "fecha_fin_programada", nullable = false)
    private Instant fechaFinProgramada;

    @Column(name = "fecha_evaluacion_programada")
    private Instant fechaEvaluacionProgramada;

    @Column(name = "inicio_ventana_aplicada", nullable = false)
    private Instant inicioVentanaAplicada;

    @Column(name = "fin_ventana_aplicada", nullable = false)
    private Instant finVentanaAplicada;

    @Column(name = "motivo", length = 1000)
    private String motivo;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    static ProgramacionOrden nueva(Orden orden, int version, Datos datos, Usuario registradoPor, Instant ahora) {
        ProgramacionOrden p = new ProgramacionOrden();
        p.orden = orden;
        p.numeroVersion = version;
        p.usuarioRegistro = registradoPor;
        p.fechaObjetivo = datos.fechaObjetivo();
        p.fechaInicioProgramada = datos.inicio();
        p.fechaFinProgramada = datos.fin();
        p.fechaEvaluacionProgramada = datos.evaluacion();
        p.inicioVentanaAplicada = datos.inicioVentana();
        p.finVentanaAplicada = datos.finVentana();
        p.motivo = datos.motivo();
        p.fechaRegistro = ahora;
        return p;
    }

    public record Datos(Instant fechaObjetivo, Instant inicio, Instant fin, Instant evaluacion,
                        Instant inicioVentana, Instant finVentana, String motivo) {
    }
}
