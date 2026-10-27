package com.komainos.catalogo.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.VersionSistemaOperativo;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Implementación del script para una versión de sistema operativo, única por script y versión (RF25) */
@Entity
@Table(name = "script_implementacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImplementacionScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_script_implementacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_script", nullable = false)
    private Script script;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_version_sistema_operativo", nullable = false)
    private VersionSistemaOperativo versionSistemaOperativo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @OneToMany(mappedBy = "implementacion", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @OrderBy("numeroVersion DESC")
    private List<VersionScript> versiones = new ArrayList<>();

    public static ImplementacionScript nueva(Script script, VersionSistemaOperativo version, Instant ahora) {
        ImplementacionScript implementacion = new ImplementacionScript();
        implementacion.script = script;
        implementacion.versionSistemaOperativo = version;
        implementacion.fechaCreacion = ahora;
        return implementacion;
    }

    /** RF42: es la versión que se fija en una MOP nueva; deshabilitar la última vuelve a la anterior habilitada */
    public Optional<VersionScript> ultimaHabilitada() {
        return versiones.stream()
                .filter(VersionScript::isHabilitada)
                .max(Comparator.comparing(VersionScript::getNumeroVersion));
    }

    public void agregarVersion(VersionScript version) {
        versiones.add(version);
    }

    public FamiliaSistemaOperativo familia() {
        return versionSistemaOperativo.getSistemaOperativo().getFamilia();
    }
}
