package com.komainos.mantenimiento.dominio;

import com.komainos.seguridad.dominio.Usuario;
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

/**
 * Version de la programacion de una orden (tabla {@code programacion_orden}).
 *
 * <p>Cada reprogramacion crea una version nueva con su motivo; las anteriores
 * se conservan como historial del cronograma (RF30, HU18 CA3).
 */
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

    /** Nulo si la registro el Sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_registro")
    private Usuario usuarioRegistro;

    @Column(name = "numero_version", nullable = false)
    private Integer numeroVersion;

    /** Fecha calculada con la periodicidad y el factor, antes de restricciones (RF64). */
    @Column(name = "fecha_objetivo", nullable = false)
    private Instant fechaObjetivo;

    /** Fecha programada (RF28). */
    @Column(name = "fecha_inicio_programada", nullable = false)
    private Instant fechaInicioProgramada;

    @Column(name = "fecha_fin_programada", nullable = false)
    private Instant fechaFinProgramada;

    /** Inicio de la evaluacion previa (RF38). */
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
