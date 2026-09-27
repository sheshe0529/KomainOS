package com.komainos.mantenimiento.dominio;

import com.komainos.inventario.dominio.GrupoMantenimiento;
import com.komainos.inventario.dominio.ModoEjecucion;
import com.komainos.inventario.dominio.NivelCriticidad;
import com.komainos.inventario.dominio.Servidor;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.shared.error.ReglaNegocioException;
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

/**
 * Orden de mantenimiento de un servidor o de un grupo (tabla {@code orden}).
 *
 * <p>Conserva los valores aplicados al generarse (criticidad, cuenta de
 * servicio, modo de ejecucion e integrantes), de modo que cambios posteriores
 * de configuracion no alteran ordenes existentes (HU13 CA7-CA8, RF20).
 *
 * <p>Toda transicion pasa por {@link #cambiarEstado}, que valida la tabla 6
 * de R2.1 y deja el registro en {@code historial_estado_orden}.
 *
 * <p>El id se asigna antes de insertar, tomado de la secuencia de identidad,
 * porque el codigo {@code OM-<anio>-<id>} lo necesita (DEC-07). Por eso la
 * entidad implementa {@link Persistable}: con id asignado Spring Data la
 * trataria como existente y haria un merge en vez de un insert.
 */
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

    /** Servidor de una orden individual; nulo en las grupales (ck_orden_objetivo_xor). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servidor")
    private Servidor servidor;

    /** Grupo de una orden grupal; nulo en las individuales. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo_mantenimiento")
    private GrupoMantenimiento grupo;

    /** Criticidad aplicada al generarse (RF76 para grupos). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_nivel_criticidad", nullable = false)
    private NivelCriticidad nivelCriticidad;

    /** Cuenta de servicio aplicada; nula mientras no exista el modulo de credenciales. */
    @Column(name = "id_cuenta_servicio")
    private Integer idCuentaServicio;

    /** Nulo cuando la genera el Sistema (RF27). */
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

    /** Solo en ordenes grupales (ck_orden_modo_grupo). */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "modo_ejecucion_aplicado", columnDefinition = "enum_modo_ejecucion")
    private ModoEjecucion modoEjecucionAplicado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    // BatchSize: al listar ordenes, sus colecciones se cargan en lotes y no una consulta por orden.
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

    /**
     * Crea una orden en estado PROGRAMADA con su primer registro de historial.
     * Los detalles y la programacion se agregan despues con el resultado del
     * algoritmo de planificacion.
     */
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

    /** Programacion vigente: la de mayor numero de version. */
    public Optional<ProgramacionOrden> programacionVigente() {
        return programaciones.stream().max(Comparator.comparingInt(ProgramacionOrden::getNumeroVersion));
    }

    public Optional<OrdenDetalle> detalleDe(Integer idServidor) {
        return detalles.stream().filter(d -> d.getServidor().getId().equals(idServidor)).findFirst();
    }

    /**
     * Unico punto por el que cambia el estado: valida la transicion (tabla 6)
     * y la registra en el historial con su motivo y autor.
     */
    public void cambiarEstado(EstadoOrden destino, Usuario autor, String motivo, Instant ahora) {
        if (!estado.puedePasarA(destino)) {
            throw new ReglaNegocioException("La orden %s no puede pasar de %s a %s"
                    .formatted(codigo, estado, destino));
        }
        historial.add(HistorialEstadoOrden.nuevo(this, autor, estado, destino, motivo, ahora));
        estado = destino;
    }

    /**
     * RF30: nueva fecha factible con motivo, conservando la programacion
     * anterior como version previa. Pasa por REPROGRAMADA y vuelve a
     * PROGRAMADA, como define la tabla 6.
     */
    public void reprogramar(ProgramacionOrden.Datos datos, List<TramoDetalle> tramos, Usuario autor,
                            String motivo, Instant ahora) {
        cambiarEstado(EstadoOrden.REPROGRAMADA, autor, motivo, ahora);
        agregarProgramacion(datos, autor, ahora);
        for (TramoDetalle tramo : tramos) {
            detalleDe(tramo.idServidor()).ifPresent(d -> d.replanificar(tramo.posicion(), tramo.inicio(), tramo.fin()));
        }
        cambiarEstado(EstadoOrden.PROGRAMADA, autor, motivo, ahora);
    }

    /** RF30: cancelacion con motivo. Los detalles no iniciados quedan como tales. */
    public void cancelar(Usuario autor, String motivo, Instant ahora) {
        cambiarEstado(EstadoOrden.CANCELADA, autor, motivo, ahora);
        detalles.forEach(OrdenDetalle::marcarNoIniciadoSiPendiente);
    }

    /**
     * RF72: retira un servidor dado de baja de una orden grupal pendiente sin
     * alterar a los demas integrantes. El detalle se conserva como historial
     * (RF20) en estado NO_INICIADO y deja de ocupar el servidor.
     */
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

    /** Tramo reservado para un servidor, resultado de la planificacion. */
    public record TramoDetalle(Integer idServidor, int posicion, Instant inicio, Instant fin) {
    }
}
