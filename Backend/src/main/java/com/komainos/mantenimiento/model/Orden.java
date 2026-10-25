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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Column(name="id_evaluacion_origen")
    private Integer idEvaluacionOrigen;

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

    /** Calculada con periodicidad y factor antes de aplicar restricciones (RF64): no cambia al reprogramar */
    @Column(name = "fecha_objetivo", nullable = false, updatable = false)
    private Instant fechaObjetivo;

    @Column(name = "fecha_inicio_programada", nullable = false)
    private Instant fechaInicioProgramada;

    @Column(name = "fecha_fin_programada", nullable = false)
    private Instant fechaFinProgramada;

    /** Inicio de la evaluación previa: el plazo de autorización antes del inicio (RF38) */
    @Column(name = "fecha_evaluacion_programada", nullable = false)
    private Instant fechaEvaluacionProgramada;

    @Column(name = "inicio_ventana_aplicada", nullable = false)
    private Instant inicioVentanaAplicada;

    @Column(name = "fin_ventana_aplicada", nullable = false)
    private Instant finVentanaAplicada;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    // BatchSize: las colecciones se cargan en lotes al listar órdenes
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("posicionEjecucion ASC")
    @BatchSize(size = 50)
    private List<OrdenDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fechaHora ASC, id ASC")
    private List<HistorialEstadoOrden> historial = new ArrayList<>();

    @Transient
    private boolean nueva;

    public static Orden nueva(Integer id, Servidor servidor, GrupoMantenimiento grupo,
                              NivelCriticidad criticidad, Integer idCuentaServicio, Usuario solicitante,
                              OrigenOrden origen, ModoEjecucion modoEjecucion, Programacion programacion,
                              Instant ahora, String motivo) {
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
        orden.fechaObjetivo = programacion.fechaObjetivo();
        orden.aplicarProgramacion(programacion);
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

    public Programacion programacion() {
        return new Programacion(fechaObjetivo, fechaInicioProgramada, fechaFinProgramada, fechaEvaluacionProgramada,
                inicioVentanaAplicada, finVentanaAplicada);
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

    /** Pasa por REPROGRAMADA y vuelve a PROGRAMADA (R2.1, tabla 6); el historial y la auditoría conservan el cambio */
    public void reprogramar(Programacion nueva, List<TramoDetalle> tramos, Usuario autor, String motivo, Instant ahora) {
        cambiarEstado(EstadoOrden.REPROGRAMADA, autor, motivo, ahora);
        aplicarProgramacion(nueva);
        for (TramoDetalle tramo : tramos) {
            detalleDe(tramo.idServidor()).ifPresent(d -> d.replanificar(tramo.posicion(), tramo.inicio(), tramo.fin()));
        }
        cambiarEstado(EstadoOrden.PROGRAMADA, autor, motivo, ahora);
    }

    public void actualizarProgramacionAutorizacion(Programacion nueva,List<TramoDetalle> tramos){
        if(estado!=EstadoOrden.PENDIENTE_AUTORIZACION)throw new ReglaNegocioException("La orden no está en autorización");
        aplicarProgramacion(nueva);for(var tramo:tramos)detalleDe(tramo.idServidor()).ifPresent(d->d.replanificar(tramo.posicion(),tramo.inicio(),tramo.fin()));
    }

    public void cancelar(Usuario autor, String motivo, Instant ahora) {
        cambiarEstado(EstadoOrden.CANCELADA, autor, motivo, ahora);
        detalles.forEach(OrdenDetalle::marcarNoIniciadoSiPendiente);
    }

    /** El detalle se conserva como historial en NO_INICIADO y deja de ocupar el servidor (RF20) */
    public void retirarServidor(Integer idServidor) {
        detalleDe(idServidor).ifPresent(OrdenDetalle::marcarNoIniciadoSiPendiente);
    }

    public void fijarCuentaServicio(Integer idCuenta){if(idCuentaServicio==null && estado==EstadoOrden.EN_EVALUACION)idCuentaServicio=idCuenta;}

    public void definirEvaluacionOrigen(Integer idEvaluacion) { if (origen != OrigenOrden.CONDICION_CRITICA) throw new IllegalStateException();idEvaluacionOrigen=idEvaluacion; }

    public boolean puedeCancelarse() {
        return estado.puedePasarA(EstadoOrden.CANCELADA);
    }

    /** Sin las colecciones: es lo que queda en la auditoría al programar o reprogramar */
    public Map<String, Object> instantaneaProgramacion() {
        Map<String, Object> v = new HashMap<>();
        v.put("fechaObjetivo", fechaObjetivo.toString());
        v.put("inicio", fechaInicioProgramada.toString());
        v.put("fin", fechaFinProgramada.toString());
        v.put("fechaEvaluacion", fechaEvaluacionProgramada.toString());
        v.put("inicioVentana", inicioVentanaAplicada.toString());
        v.put("finVentana", finVentanaAplicada.toString());
        return v;
    }

    /** La fecha objetivo se fija al generar la orden y no se toca aquí */
    private void aplicarProgramacion(Programacion p) {
        if (p.fin().isBefore(p.inicio()) || p.finVentana().isBefore(p.inicioVentana())) {
            throw new IllegalArgumentException("El fin de la programación no puede ser anterior a su inicio");
        }
        fechaInicioProgramada = p.inicio();
        fechaFinProgramada = p.fin();
        fechaEvaluacionProgramada = p.evaluacion();
        inicioVentanaAplicada = p.inicioVentana();
        finVentanaAplicada = p.finVentana();
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
