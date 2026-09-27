package com.komainos.inventario.dominio;

import com.komainos.seguridad.dominio.Usuario;
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
 * Solicitud de baja de un servidor (RF72), tabla {@code solicitud_baja}.
 *
 * <p>Queda PENDIENTE mientras exista un mantenimiento en curso y pasa a
 * APLICADA al ejecutarse la baja. Se conserva como historial aunque el
 * servidor se reactive despues.
 */
@Entity
@Table(name = "solicitud_baja")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudBaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_baja")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false)
    private Servidor servidor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_solicitante", nullable = false)
    private Usuario solicitante;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_solicitud_baja")
    private EstadoSolicitudBaja estado;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "fecha_solicitud", nullable = false)
    private Instant fechaSolicitud;

    /** Obligatoria solo cuando esta aplicada (ck_solicitud_baja_fecha). */
    @Column(name = "fecha_aplicacion")
    private Instant fechaAplicacion;

    public static SolicitudBaja nueva(Servidor servidor, Usuario solicitante, String motivo, Instant ahora) {
        SolicitudBaja solicitud = new SolicitudBaja();
        solicitud.servidor = servidor;
        solicitud.solicitante = solicitante;
        solicitud.motivo = motivo;
        solicitud.estado = EstadoSolicitudBaja.PENDIENTE;
        solicitud.fechaSolicitud = ahora;
        return solicitud;
    }

    public void aplicar(Instant ahora) {
        estado = EstadoSolicitudBaja.APLICADA;
        fechaAplicacion = ahora;
    }
}
