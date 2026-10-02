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
import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Las transiciones de estado viven aquí y no en el servicio */
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_responsable", nullable = false)
    private Usuario responsable;

    @Column(name = "hostname", nullable = false, length = 255)
    private String hostname;

    @OneToMany(mappedBy = "servidor", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Setter(AccessLevel.NONE)
    private Set<DireccionIp> direcciones = new LinkedHashSet<>();

    /** IP principal leída en la misma consulta: listados y órdenes no cargan todas las direcciones */
    @Formula("(select d.direccion from direccion_ip d where d.id_servidor = id_servidor and d.principal)")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String direccionPrincipal;

    @Formula("(select count(*) from direccion_ip d where d.id_servidor = id_servidor)")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private int cantidadDireccionesRegistradas;

    /** Virtual DataCenter (antes datacenter) */
    @Column(name = "vdc", length = 255)
    private String vdc;

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

    /** Opcionales y mayores que cero (ck_servidor_recursos) */
    @Column(name = "cantidad_cpu")
    private Integer cantidadCpu;

    @Column(name = "ram_gb", precision = 7, scale = 2)
    private BigDecimal ramGb;

    @Column(name = "hd_virtual_gb", precision = 10, scale = 2)
    private BigDecimal hdVirtualGb;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_servidor")
    private EstadoServidor estado;

    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @OneToMany(mappedBy = "servidor", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("diaInicio ASC, horaInicio ASC")
    private Set<VentanaMantenimiento> ventanas = new LinkedHashSet<>();

    public static Servidor nuevo() {
        Servidor servidor = new Servidor();
        servidor.estado = EstadoServidor.PENDIENTE_DE_CONFIGURACION;
        return servidor;
    }

    public boolean estaDadoDeBaja() {
        return estado == EstadoServidor.DADO_DE_BAJA;
    }

    public void exigirNoDadoDeBaja(String accion) {
        if (estaDadoDeBaja()) {
            throw new ReglaNegocioException(
                    "El servidor %s está dado de baja; reactívelo antes de %s".formatted(hostname, accion));
        }
    }

    public void activarPorConfiguracion() {
        exigirNoDadoDeBaja("configurarlo");
        estado = EstadoServidor.ACTIVO;
    }

    /** Quien llama ya comprobó que no hay mantenimiento en curso y retiró los pendientes */
    public void aplicarBaja() {
        if (estaDadoDeBaja()) {
            throw new ReglaNegocioException("El servidor %s ya se encuentra dado de baja".formatted(hostname));
        }
        estado = EstadoServidor.DADO_DE_BAJA;
    }

    /** Vuelve a quedar pendiente de configuración: no genera mantenimientos hasta completarla */
    public void reactivar() {
        if (!estaDadoDeBaja()) {
            throw new ReglaNegocioException(
                    "Solo se puede reactivar un servidor dado de baja; %s está en estado %s"
                            .formatted(hostname, estado));
        }
        estado = EstadoServidor.PENDIENTE_DE_CONFIGURACION;
    }

    public boolean esVisiblePara(Integer usuarioId) {
        return responsable != null && responsable.getId().equals(usuarioId);
    }

    /** Define el canal remoto y con él los mecanismos de autenticación admitidos */
    public FamiliaSistemaOperativo familia() {
        return versionSistemaOperativo.getSistemaOperativo().getFamilia();
    }

    /** Si las direcciones ya están cargadas (recién modificadas) se leen de ellas, si no, del valor consultado */
    public String getDireccionIp() {
        if (Hibernate.isInitialized(direcciones) && !direcciones.isEmpty()) {
            return direcciones.stream().filter(DireccionIp::isPrincipal).map(DireccionIp::getDireccion)
                    .findFirst().orElse(null);
        }
        return direccionPrincipal;
    }

    public int getCantidadDirecciones() {
        return Hibernate.isInitialized(direcciones) && !direcciones.isEmpty()
                ? direcciones.size() : cantidadDireccionesRegistradas;
    }

    public List<DireccionIp> direccionesOrdenadas() {
        return direcciones.stream()
                .sorted(Comparator.comparing((DireccionIp d) -> !d.isPrincipal()).thenComparing(DireccionIp::getDireccion))
                .toList();
    }

    public List<String> direccionesAdicionales() {
        return direccionesOrdenadas().stream().filter(d -> !d.isPrincipal()).map(DireccionIp::getDireccion).toList();
    }

    /** Conserva las IP que siguen para no borrar y recrear una que solo cambia de principal a adicional */
    public void reemplazarDirecciones(String principal, List<String> adicionales) {
        List<String> todas = new ArrayList<>();
        todas.add(principal);
        todas.addAll(adicionales);
        direcciones.removeIf(d -> !todas.contains(d.getDireccion()));
        Set<String> actuales = new HashSet<>();
        direcciones.forEach(d -> actuales.add(d.getDireccion()));
        for (String direccion : todas) {
            if (!actuales.contains(direccion)) {
                direcciones.add(new DireccionIp(this, direccion, false));
            }
        }
        direcciones.forEach(d -> d.marcarPrincipal(d.getDireccion().equals(principal)));
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
