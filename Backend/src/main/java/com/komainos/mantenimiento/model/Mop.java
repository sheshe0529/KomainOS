package com.komainos.mantenimiento.model;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.VersionScript;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Método de procedimiento de un detalle de orden (RF41): las tareas activadas, una vez cada una y por prioridad */
@Entity
@Table(name = "mop")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mop")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden_detalle", nullable = false)
    private OrdenDetalle detalle;

    @Column(name = "fecha_generacion", nullable = false)
    private Instant fechaGeneracion;

    @OneToMany(mappedBy = "mop", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @OrderBy("ordenEjecucion ASC")
    private List<MopTarea> tareas = new ArrayList<>();

    /**
     * Las tareas llegan ordenadas por prioridad y sin repetir (RF41).
     *
     * @param version de cada tarea, en el mismo orden; nula si no hay implementación compatible (RF42)
     */
    public static Mop generar(OrdenDetalle detalle, List<Tarea> tareas, List<VersionScript> versiones,
                              Integer idCuentaServicio, Instant ahora) {
        Mop mop = new Mop();
        mop.detalle = detalle;
        mop.fechaGeneracion = ahora;
        for (int i = 0; i < tareas.size(); i++) {
            mop.tareas.add(MopTarea.fijar(mop, tareas.get(i), versiones.get(i), idCuentaServicio, i + 1));
        }
        return mop;
    }

    public int duracionTotal() {
        return tareas.stream().mapToInt(MopTarea::getDuracionEstimadaMinutos).sum();
    }

    /** RF45: la mayor afectación entre las tareas */
    public NivelAfectacion afectacion() {
        return tareas.stream().map(MopTarea::getNivelAfectacion)
                .reduce(NivelAfectacion.SIN_AFECTACION, NivelAfectacion::mayor);
    }

    public boolean tieneTareasSinImplementacion() {
        return tareas.stream().anyMatch(t -> t.getVersion() == null);
    }

    public Optional<MopTarea> siguientePendiente() {
        return tareas.stream().filter(t -> t.getEstado() == EstadoTareaMop.PENDIENTE).findFirst();
    }

    public void omitirPendientes() {
        tareas.forEach(MopTarea::omitir);
    }
}
