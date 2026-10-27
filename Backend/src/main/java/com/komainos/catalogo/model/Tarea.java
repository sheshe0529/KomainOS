package com.komainos.catalogo.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tarea de mantenimiento (RF22, RF23): se activa cuando alguno de sus umbrales sale del nivel normal y se ejecuta con
 * su script. Menor prioridad, primero; la duración estimada no supera el máximo por tarea.
 */
@Entity
@Table(name = "tarea")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarea")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_script", nullable = false)
    private Script script;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_afectacion", nullable = false, columnDefinition = "enum_nivel_afectacion")
    private NivelAfectacion nivelAfectacion;

    @Column(name = "prioridad", nullable = false)
    private Integer prioridad;

    @Column(name = "duracion_estimada_minutos", nullable = false)
    private Integer duracionEstimadaMinutos;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "umbral_tarea", joinColumns = @JoinColumn(name = "id_tarea"))
    @Column(name = "id_umbral")
    private Set<Integer> idsUmbrales = new LinkedHashSet<>();

    public static Tarea nueva() {
        return new Tarea();
    }

    public void actualizar(Script script, DatosTarea datos, Collection<Integer> umbrales) {
        this.script = script;
        this.nombre = datos.nombre().trim();
        this.descripcion = datos.descripcion() == null || datos.descripcion().isBlank() ? null : datos.descripcion().trim();
        this.nivelAfectacion = datos.afectacion();
        this.prioridad = datos.prioridad();
        this.duracionEstimadaMinutos = datos.duracionMinutos();
        this.activo = datos.activo();
        idsUmbrales.clear();
        idsUmbrales.addAll(umbrales);
    }

    public record DatosTarea(String nombre, String descripcion, NivelAfectacion afectacion, int prioridad,
                             int duracionMinutos, boolean activo) {
    }
}
