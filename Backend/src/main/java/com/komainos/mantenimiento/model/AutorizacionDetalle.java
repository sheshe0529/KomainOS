package com.komainos.mantenimiento.model;

import com.komainos.seguridad.model.Usuario;
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

/** Decisión de un aprobador dentro de una ronda (RF47, RF48): usuario, fecha, decisión y motivo */
@Entity
@Table(name = "autorizacion_detalle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutorizacionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_autorizacion_detalle")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitud_autorizacion", nullable = false)
    private SolicitudAutorizacion solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_decision")
    private Usuario usuarioDecision;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_aprobador", nullable = false, columnDefinition = "enum_tipo_aprobador")
    private TipoAprobador tipo;

    @Column(name = "orden_secuencia", nullable = false)
    private Integer ordenSecuencia;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_autorizacion_detalle")
    private EstadoAutorizacion estado;

    /** Cuándo se pidió de verdad: la del administrador se actualiza al aprobar el responsable (RF44) */
    @Column(name = "fecha_solicitud", nullable = false)
    private Instant fechaSolicitud;

    @Column(name = "fecha_decision")
    private Instant fechaDecision;

    @Column(name = "fecha_propuesta")
    private Instant fechaPropuesta;

    @Column(name = "motivo", length = 1000)
    private String motivo;

    static AutorizacionDetalle nueva(SolicitudAutorizacion solicitud, TipoAprobador tipo, int secuencia, Instant ahora) {
        AutorizacionDetalle d = new AutorizacionDetalle();
        d.solicitud = solicitud;
        d.tipo = tipo;
        d.ordenSecuencia = secuencia;
        d.estado = EstadoAutorizacion.PENDIENTE;
        d.fechaSolicitud = ahora;
        return d;
    }

    public boolean estaPendiente() {
        return estado == EstadoAutorizacion.PENDIENTE;
    }

    public void solicitar(Instant ahora) {
        if (estaPendiente()) {
            fechaSolicitud = ahora;
        }
    }

    /** RF48: solo el responsable propone otra fecha, y debe ser futura */
    public void decidir(EstadoAutorizacion decision, Usuario autor, String motivo, Instant propuesta, Instant ahora) {
        if (!estaPendiente()) {
            throw new ReglaNegocioException("Esta autorización ya fue respondida");
        }
        if (decision == EstadoAutorizacion.PENDIENTE || decision == EstadoAutorizacion.VENCIDA) {
            throw new ReglaNegocioException("Indique si aprueba, rechaza o propone otra fecha");
        }
        if (decision == EstadoAutorizacion.FECHA_PROPUESTA
                && (tipo != TipoAprobador.RESPONSABLE || propuesta == null || !propuesta.isAfter(ahora))) {
            throw new ReglaNegocioException("Solo el responsable propone otra fecha, y debe ser futura");
        }
        this.estado = decision;
        this.usuarioDecision = autor;
        this.motivo = motivo;
        this.fechaPropuesta = decision == EstadoAutorizacion.FECHA_PROPUESTA ? propuesta : null;
        this.fechaDecision = ahora;
    }

    void vencer(Instant ahora) {
        estado = EstadoAutorizacion.VENCIDA;
        motivo = "Plazo compartido vencido sin decisión";
        fechaDecision = ahora;
    }
}
