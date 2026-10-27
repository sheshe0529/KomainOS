package com.komainos.catalogo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo lógico del script (RF24, RF25). El código vive en las versiones de cada implementación: se publica siempre
 * como una versión nueva y nunca se edita (RF26). El tipo no cambia después de registrarlo.
 */
@Entity
@Table(name = "script")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_script")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo", nullable = false, columnDefinition = "enum_tipo_script")
    private TipoScript tipo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    // BatchSize: los listados paginados cargan las colecciones en lotes, sin paginar en memoria
    @OneToMany(mappedBy = "script", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<ImplementacionScript> implementaciones = new ArrayList<>();

    public static Script nuevo(String nombre, TipoScript tipo, String descripcion, boolean activo) {
        Script script = new Script();
        script.tipo = tipo;
        script.actualizar(nombre, descripcion, activo);
        return script;
    }

    public void actualizar(String nombre, String descripcion, boolean activo) {
        this.nombre = nombre.trim();
        this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        this.activo = activo;
    }

    public void agregarImplementacion(ImplementacionScript implementacion) {
        implementaciones.add(implementacion);
    }

    public Optional<ImplementacionScript> implementacionPara(Integer idVersionSistemaOperativo) {
        return implementaciones.stream()
                .filter(i -> i.getVersionSistemaOperativo().getId().equals(idVersionSistemaOperativo))
                .findFirst();
    }

    /** RF23: una tarea solo puede usar un script activo con al menos una versión habilitada */
    public boolean tieneVersionHabilitada() {
        return implementaciones.stream().anyMatch(i -> i.ultimaHabilitada().isPresent());
    }

    public boolean esAnalisis() {
        return tipo == TipoScript.ANALISIS;
    }
}
