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

/**
 * Conformidad del responsable con el resultado (RF56, RF57), una por orden: un grupo tiene un solo responsable (RF20).
 * El plazo corre desde la solicitud con las horas de la criticidad; vencerlo no presume conformidad.
 */
@Entity
@Table(name = "validacion_funcional")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidacionFuncional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_validacion_funcional")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_responsable", nullable = false)
    private Usuario responsable;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_validacion_funcional")
    private EstadoValidacionFuncional estado;

    @Column(name = "fecha_solicitud", nullable = false)
    private Instant fechaSolicitud;

    @Column(name = "fecha_limite", nullable = false)
    private Instant fechaLimite;

    @Column(name = "fecha_respuesta")
    private Instant fechaRespuesta;

    @Column(name = "observaciones")
    private String observaciones;

    public static ValidacionFuncional solicitar(Orden orden, Usuario responsable, Instant ahora, Instant limite) {
        ValidacionFuncional v = new ValidacionFuncional();
        v.orden = orden;
        v.responsable = responsable;
        v.estado = EstadoValidacionFuncional.PENDIENTE;
        v.fechaSolicitud = ahora;
        v.fechaLimite = limite;
        return v;
    }

    public boolean estaPendiente() {
        return estado == EstadoValidacionFuncional.PENDIENTE;
    }

    public boolean vencida(Instant ahora) {
        return !ahora.isBefore(fechaLimite);
    }

    public void responder(boolean conforme, String observaciones, Instant ahora) {
        if (!estaPendiente()) {
            throw new ReglaNegocioException("La validación ya fue respondida");
        }
        if (vencida(ahora)) {
            throw new ReglaNegocioException("El plazo de validación venció");
        }
        this.estado = conforme ? EstadoValidacionFuncional.CONFORME : EstadoValidacionFuncional.RECHAZADA;
        this.observaciones = observaciones;
        this.fechaRespuesta = ahora;
    }

    /** RF57, EA06: sin respuesta dentro del plazo se registra vencida, nunca conforme */
    public void vencer(Instant ahora) {
        if (estaPendiente()) {
            estado = EstadoValidacionFuncional.VENCIDA;
            observaciones = "Sin respuesta dentro del plazo";
            fechaRespuesta = ahora;
        }
    }

    public boolean esConforme() {
        return estado == EstadoValidacionFuncional.CONFORME;
    }
}
