package com.komainos.mantenimiento.model;

import com.komainos.seguridad.model.Usuario;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

@Entity
@Table(name = "historial_estado_orden")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistorialEstadoOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial_estado_orden")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado_anterior", columnDefinition = "enum_estado_orden")
    private EstadoOrden estadoAnterior;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado_nuevo", nullable = false, columnDefinition = "enum_estado_orden")
    private EstadoOrden estadoNuevo;

    @Column(name = "motivo", length = 1000)
    private String motivo;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    static HistorialEstadoOrden nuevo(Orden orden, Usuario usuario, EstadoOrden anterior, EstadoOrden nuevo,
                                      String motivo, Instant ahora) {
        HistorialEstadoOrden h = new HistorialEstadoOrden();
        h.orden = orden;
        h.usuario = usuario;
        h.estadoAnterior = anterior;
        h.estadoNuevo = nuevo;
        h.motivo = motivo;
        h.fechaHora = ahora;
        return h;
    }
}
