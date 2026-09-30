package com.komainos.inventario.model;

import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.Tiempo;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Servidor virtual del inventario (RF09, RF10), tabla {@code servidor}.
 *
 * <p>Todas las asociaciones son LAZY. Con {@code open-in-view} en false,
 * leer una asociacion fuera de la transaccion falla de inmediato en vez de
 * disparar una consulta oculta por fila.
 *
 * <p>Las transiciones de estado viven aqui y no en el servicio: son
 * invariantes del activo que no dependen de quien las pida.
 */
@Entity
@Table(name = "servidor")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Servidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servidor")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_version_sistema_operativo", nullable = false)
    private VersionSistemaOperativo versionSistemaOperativo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_entorno", nullable = false)
    private Entorno entorno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_nivel_criticidad", nullable = false)
    private NivelCriticidad nivelCriticidad;

    /** Quien autoriza y valida los mantenimientos de este servidor. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_responsable", nullable = false)
    private Usuario responsable;

    /** Identidad de negocio del activo; unica (uq_servidor_hostname). */
    @Column(name = "hostname", nullable = false, length = 255)
    private String hostname;

    /** IPv4 o IPv6; unica (uq_servidor_direccion_ip). */
    @Column(name = "direccion_ip", nullable = false, length = 45)
    private String direccionIp;

    @Column(name = "datacenter", length = 255)
    private String datacenter;

    @Column(name = "servidor_fisico", length = 255)
    private String servidorFisico;

    @Column(name = "vlan", length = 100)
    private String vlan;

    @Column(name = "cluster", length = 255)
    private String cluster;

    @Column(name = "dns", length = 255)
    private String dns;

    @Column(name = "plataforma", length = 255)
    private String plataforma;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_servidor")
    private EstadoServidor estado;

    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    /** Ventanas permisivas (RF18, RF19). Se reemplazan como conjunto. */
    @OneToMany(mappedBy = "servidor", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("diaInicio ASC, horaInicio ASC")
    private Set<VentanaMantenimiento> ventanas = new LinkedHashSet<>();

    /** Alta de un servidor: nace pendiente de configuracion (HU06 CA3). */
    public static Servidor nuevo() {
        Servidor servidor = new Servidor();
        servidor.estado = EstadoServidor.PENDIENTE_DE_CONFIGURACION;
        return servidor;
    }

    public boolean estaDadoDeBaja() {
        return estado == EstadoServidor.DADO_DE_BAJA;
    }

    /** Un servidor dado de baja conserva su historial y no se modifica (RF72). */
    public void exigirNoDadoDeBaja(String accion) {
        if (estaDadoDeBaja()) {
            throw new ReglaNegocioException(
                    "El servidor %s está dado de baja; reactívelo antes de %s".formatted(hostname, accion));
        }
    }

    /** Guardar su configuracion de mantenimiento lo habilita (DEC-14). */
    public void activarPorConfiguracion() {
        exigirNoDadoDeBaja("configurarlo");
        estado = EstadoServidor.ACTIVO;
    }

    /**
     * Aplica la baja (RF72). Quien llama ya comprobo que no hay un
     * mantenimiento en curso y retiro los pendientes.
     */
    public void aplicarBaja() {
        if (estaDadoDeBaja()) {
            throw new ReglaNegocioException("El servidor %s ya se encuentra dado de baja".formatted(hostname));
        }
        estado = EstadoServidor.DADO_DE_BAJA;
    }

    /**
     * Reactiva un servidor dado de baja (RF73). Vuelve a quedar pendiente de
     * configuracion: no genera mantenimientos hasta completarla.
     */
    public void reactivar() {
        if (!estaDadoDeBaja()) {
            throw new ReglaNegocioException(
                    "Solo se puede reactivar un servidor dado de baja; %s está en estado %s"
                            .formatted(hostname, estado));
        }
        estado = EstadoServidor.PENDIENTE_DE_CONFIGURACION;
    }

    /** Alcance del responsable (RF11, HU10 CA6). */
    public boolean esVisiblePara(Integer usuarioId) {
        return responsable != null && responsable.getId().equals(usuarioId);
    }

    public void reemplazarVentanas(List<VentanaMantenimiento> nuevas) {
        ventanas.clear();
        for (VentanaMantenimiento ventana : nuevas) {
            ventana.setServidor(this);
            ventanas.add(ventana);
        }
    }

    @PrePersist
    void alInsertar() {
        Instant ahora = Tiempo.ahora();
        fechaAlta = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = Tiempo.ahora();
    }
}
