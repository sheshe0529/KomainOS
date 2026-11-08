package com.komainos.mantenimiento.model;

import com.komainos.inventario.model.GrupoMantenimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.Objects;

/** Agrupa los análisis de una misma revisión y fija el inicio para el calendario propio del grupo. */
@Entity
@Table(name = "revision_periodica_grupo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionPeriodicaGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_revision_periodica_grupo")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_mantenimiento", nullable = false, updatable = false)
    private GrupoMantenimiento grupo;

    @Column(name = "fecha_inicio", nullable = false, updatable = false)
    private Instant fechaInicio;

    public static RevisionPeriodicaGrupo iniciar(GrupoMantenimiento grupo, Instant ahora) {
        RevisionPeriodicaGrupo revision = new RevisionPeriodicaGrupo();
        revision.grupo = Objects.requireNonNull(grupo);
        revision.fechaInicio = Objects.requireNonNull(ahora);
        return revision;
    }
}
