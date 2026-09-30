package com.komainos.mantenimiento.model;

import com.komainos.inventario.model.Servidor;
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
 * Participacion de un servidor en una orden (tabla {@code orden_detalle}).
 * Una orden individual tiene un detalle; una grupal, uno por integrante al
 * generarse (RF20). Sus fechas previstas son la reserva que respeta la
 * planificacion (RF28, RF51).
 */
@Entity
@Table(name = "orden_detalle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdenDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_detalle")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false)
    private Servidor servidor;

    @Column(name = "posicion_ejecucion", nullable = false)
    private Integer posicionEjecucion;

    /** Se define al generar las MOP (RF58, DEC-11). */
    @Column(name = "es_servidor_piloto", nullable = false)
    private boolean esServidorPiloto;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_detalle_orden")
    private EstadoDetalleOrden estado;

    @Column(name = "fecha_prevista_inicio")
    private Instant fechaPrevistaInicio;

    @Column(name = "fecha_prevista_fin")
    private Instant fechaPrevistaFin;

    @Column(name = "fecha_real_inicio")
    private Instant fechaRealInicio;

    @Column(name = "fecha_real_fin")
    private Instant fechaRealFin;

    static OrdenDetalle nuevo(Orden orden, Servidor servidor, int posicion, Instant inicio, Instant fin) {
        OrdenDetalle detalle = new OrdenDetalle();
        detalle.orden = orden;
        detalle.servidor = servidor;
        detalle.posicionEjecucion = posicion;
        detalle.esServidorPiloto = false;
        detalle.estado = EstadoDetalleOrden.PENDIENTE;
        detalle.fechaPrevistaInicio = inicio;
        detalle.fechaPrevistaFin = fin;
        return detalle;
    }

    void replanificar(int posicion, Instant inicio, Instant fin) {
        posicionEjecucion = posicion;
        fechaPrevistaInicio = inicio;
        fechaPrevistaFin = fin;
    }

    void marcarNoIniciadoSiPendiente() {
        if (estado.puedePasarA(EstadoDetalleOrden.NO_INICIADO)) {
            estado = EstadoDetalleOrden.NO_INICIADO;
        }
    }
}
