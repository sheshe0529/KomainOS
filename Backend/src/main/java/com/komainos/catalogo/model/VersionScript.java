package com.komainos.catalogo.model;

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

/**
 * Versión inmutable del código (RF26): número, contenido, notas y parámetros no cambian después de publicarse, así las
 * MOP pendientes e históricas conservan exactamente lo que fijaron. Solo se habilita o deshabilita.
 */
@Entity
@Table(name = "script_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersionScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_script_version")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_script_implementacion", nullable = false)
    private ImplementacionScript implementacion;

    @Column(name = "numero_version", nullable = false, updatable = false)
    private Integer numeroVersion;

    @Column(name = "contenido", nullable = false, updatable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "notas_cambio", length = 1000, updatable = false)
    private String notasCambio;

    @Column(name = "habilitada", nullable = false)
    private boolean habilitada = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @OrderBy("orden ASC")
    private List<ParametroScript> parametros = new ArrayList<>();

    public static VersionScript nueva(ImplementacionScript implementacion, int numero, String contenido, String notas,
                                      Instant ahora) {
        VersionScript version = new VersionScript();
        version.implementacion = implementacion;
        version.numeroVersion = numero;
        version.contenido = contenido;
        version.notasCambio = notas == null || notas.isBlank() ? null : notas.trim();
        version.fechaCreacion = ahora;
        return version;
    }

    public void agregarParametro(ParametroScript parametro) {
        parametros.add(parametro);
    }

    public void habilitar(boolean valor) {
        habilitada = valor;
    }
}
