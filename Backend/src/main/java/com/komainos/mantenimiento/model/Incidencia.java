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
 * Incidencia de un servidor en una orden (RF61, RF62). Se atiende con diagnóstico, acciones y resultado; cerrarla no
 * reanuda las tareas fallidas ni las no iniciadas (HU28 CA3).
 */
@Entity
@Table(name = "incidencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_incidencia")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden_detalle", nullable = false)
    private OrdenDetalle detalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_atencion")
    private Usuario usuarioAtencion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "origen", nullable = false, columnDefinition = "enum_origen_incidencia")
    private OrigenIncidencia origen;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_incidencia")
    private EstadoIncidencia estado;

    @Column(name = "error")
    private String error;

    @Column(name = "diagnostico")
    private String diagnostico;

    @Column(name = "acciones_realizadas")
    private String accionesRealizadas;

    @Column(name = "resultado", length = 1000)
    private String resultado;

    @Column(name = "fecha_apertura", nullable = false)
    private Instant fechaApertura;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    public static Incidencia abrir(OrdenDetalle detalle, OrigenIncidencia origen, String error, Instant ahora) {
        Incidencia i = new Incidencia();
        i.detalle = detalle;
        i.origen = origen;
        i.error = error;
        i.estado = EstadoIncidencia.ABIERTA;
        i.fechaApertura = ahora;
        return i;
    }

    /** CP150: para cerrarla deben registrarse el diagnóstico y el resultado */
    public void atender(Usuario autor, String diagnostico, String acciones, String resultado, boolean cerrar,
                        Instant ahora) {
        if (estado == EstadoIncidencia.CERRADA) {
            throw new ReglaNegocioException("La incidencia ya está cerrada");
        }
        if (cerrar && (vacio(diagnostico) || vacio(resultado))) {
            throw new ReglaNegocioException("Para cerrar la incidencia registre el diagnóstico y el resultado");
        }
        this.usuarioAtencion = autor;
        this.diagnostico = diagnostico;
        this.accionesRealizadas = acciones;
        this.resultado = resultado;
        this.estado = cerrar ? EstadoIncidencia.CERRADA : EstadoIncidencia.EN_ATENCION;
        this.fechaCierre = cerrar ? ahora : null;
    }

    public boolean estaCerrada() {
        return estado == EstadoIncidencia.CERRADA;
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
