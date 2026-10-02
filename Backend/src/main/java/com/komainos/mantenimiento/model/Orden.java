package com.komainos.mantenimiento.model;

import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Persistable: el id se asigna antes de insertar por el código OM, sin esto Spring Data haría merge en vez de insert */
@Entity
@Table(name = "orden")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orden implements Persistable<Integer> {

    @Id
    @Column(name = "id_orden")
    private Integer id;

    @Column(name = "codigo", nullable = false, length = 100)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servidor")
    private Servidor servidor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo_mantenimiento")
    private GrupoMantenimiento grupo;

    /** Criticidad aplicada al generarse: no cambia con la configuración posterior */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_nivel_criticidad", nullable = false)
    private NivelCriticidad nivelCriticidad;

    /** Nula mientras no exista el módulo de credenciales */
    @Column(name = "id_cuenta_servicio")
    private Integer idCuentaServicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_solicitante")
    private Usuario usuarioSolicitante;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "origen", nullable = false, columnDefinition = "enum_origen_orden")
    private OrigenOrden origen;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_orden")
    private EstadoOrden estado;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "modo_ejecucion_aplicado", columnDefinition = "enum_modo_ejecucion")
    private ModoEjecucion modoEjecucionAplicado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    // BatchSize: las colecciones se cargan en lotes al listar órdenes
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicionEjecucion ASC")
    @BatchSize(size = 50)
    private List<OrdenDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroVersion ASC")
    @BatchSize(size = 50)
    private List<ProgramacionOrden> programaciones = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fechaHora ASC, id ASC")
    private List<HistorialEstadoOrden> historial = new ArrayList<>();

    @Transient
    private boolean nueva;

    public static Orden nueva(Integer id, Servidor servidor, GrupoMantenimiento grupo,
                              NivelCriticidad criticidad, Integer idCuentaServicio, Usuario solicitante,
                              OrigenOrden origen, ModoEjecucion modoEjecucion, Instant ahora, String motivo) {
        if ((servidor == null) == (grupo == null)) {
            throw new IllegalArgumentException("La orden se dirige a un servidor o a un grupo, no a ambos ni a ninguno");
        }
        Orden orden = new Orden();
        orden.nueva = true;
        orden.id = id;
        orden.codigo = "OM-%d-%04d".formatted(ahora.atZone(ZoneOffset.UTC).getYear(), id);
        orden.servidor = servidor;
        orden.grupo = grupo;
        orden.nivelCriticidad = criticidad;
        orden.idCuentaServicio = idCuentaServicio;
        orden.usuarioSolicitante = solicitante;
        orden.origen = origen;
        orden.modoEjecucionAplicado = grupo != null ? modoEjecucion : null;
        orden.estado = EstadoOrden.PROGRAMADA;
        orden.fechaCreacion = ahora;
        orden.historial.add(HistorialEstadoOrden.nuevo(orden, solicitante, null, EstadoOrden.PROGRAMADA, motivo, ahora));
        return orden;
    }

    public boolean esGrupal() {
        return grupo != null;
    }

    public PrioridadOrden prioridad() {
        return origen.prioridad();
    }

    public void agregarDetalle(Servidor servidorDetalle, int posicion, Instant previstaInicio, Instant previstaFin) {
        detalles.add(OrdenDetalle.nuevo(this, servidorDetalle, posicion, previstaInicio, previstaFin));
    }

    public void agregarProgramacion(ProgramacionOrden.Datos datos, Usuario registradoPor, Instant ahora) {
        int version = programaciones.stream().mapToInt(ProgramacionOrden::getNumeroVersion).max().orElse(0) + 1;
        programaciones.add(ProgramacionOrden.nueva(this, version, datos, registradoPor, ahora));
    }

    /** Vigente: la de mayor número de versión */
    public Optional<ProgramacionOrden> programacionVigente() {
        return programaciones.stream().max(Comparator.comparingInt(ProgramacionOrden::getNumeroVersion));
    }

    public Optional<OrdenDetalle> detalleDe(Integer idServidor) {
        return detalles.stream().filter(d -> d.getServidor().getId().equals(idServidor)).findFirst();
    }

    /** Único punto por el que cambia el estado: valida la transición y la registra en el historial */
    public void cambiarEstado(EstadoOrden destino, Usuario autor, String motivo, Instant ahora) {
        if (!estado.puedePasarA(destino)) {
            throw new ReglaNegocioException("La orden %s no puede pasar de %s a %s"
                    .formatted(codigo, estado, destino));
        }
        historial.add(HistorialEstadoOrden.nuevo(this, autor, estado, destino, motivo, ahora));
        estado = destino;
    }

    /** Pasa por REPROGRAMADA y vuelve a PROGRAMADA (R2.1, tabla 6) */
    public void reprogramar(ProgramacionOrden.Datos datos, List<TramoDetalle> tramos, Usuario autor,
                            String motivo, Instant ahora) {
        cambiarEstado(EstadoOrden.REPROGRAMADA, autor, motivo, ahora);
        agregarProgramacion(datos, autor, ahora);
        for (TramoDetalle tramo : tramos) {
            detalleDe(tramo.idServidor()).ifPresent(d -> d.replanificar(tramo.posicion(), tramo.inicio(), tramo.fin()));
        }
        cambiarEstado(EstadoOrden.PROGRAMADA, autor, motivo, ahora);
    }

    public void cancelar(Usuario autor, String motivo, Instant ahora) {
        cambiarEstado(EstadoOrden.CANCELADA, autor, motivo, ahora);
        detalles.forEach(OrdenDetalle::marcarNoIniciadoSiPendiente);
    }

    /** El detalle se conserva como historial en NO_INICIADO y deja de ocupar el servidor (RF20) */
    public void retirarServidor(Integer idServidor) {
        detalleDe(idServidor).ifPresent(OrdenDetalle::marcarNoIniciadoSiPendiente);
    }

    public boolean puedeCancelarse() {
        return estado.puedePasarA(EstadoOrden.CANCELADA);
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostPersist
    @PostLoad
    void marcarPersistida() {
        nueva = false;
    }

    public record TramoDetalle(Integer idServidor, int posicion, Instant inicio, Instant fin) {
    }
}
