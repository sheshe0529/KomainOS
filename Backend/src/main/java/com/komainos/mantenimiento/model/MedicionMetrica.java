package com.komainos.mantenimiento.model;

import com.komainos.inventario.model.Metrica;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Valor de una métrica en una evaluación, única por métrica e instancia, con los umbrales evaluados (R2.4) */
@Entity
@Table(name = "medicion_metrica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicionMetrica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_medicion_metrica")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evaluacion", nullable = false)
    private Evaluacion evaluacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_metrica", nullable = false)
    private Metrica metrica;

    /** Nulo en las métricas de lista */
    @Column(name = "valor")
    private BigDecimal valor;

    @Column(name = "instancia", nullable = false)
    private String instancia = "";

    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "medicion_lista_item", joinColumns = @JoinColumn(name = "id_medicion_metrica"))
    @Column(name = "valor", length = 255)
    private Set<String> elementos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "medicion", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<EvaluacionUmbral> umbrales = new ArrayList<>();

    public static MedicionMetrica escalar(Evaluacion evaluacion, Metrica metrica, String instancia, BigDecimal valor) {
        MedicionMetrica medicion = nueva(evaluacion, metrica, instancia);
        medicion.valor = valor;
        return medicion;
    }

    public static MedicionMetrica lista(Evaluacion evaluacion, Metrica metrica, String instancia,
                                        Collection<String> elementos) {
        MedicionMetrica medicion = nueva(evaluacion, metrica, instancia);
        medicion.elementos.addAll(elementos);
        return medicion;
    }

    public void agregarUmbral(EvaluacionUmbral evaluacionUmbral) {
        umbrales.add(evaluacionUmbral);
    }

    private static MedicionMetrica nueva(Evaluacion evaluacion, Metrica metrica, String instancia) {
        MedicionMetrica medicion = new MedicionMetrica();
        medicion.evaluacion = evaluacion;
        medicion.metrica = metrica;
        medicion.instancia = instancia == null ? "" : instancia;
        return medicion;
    }
}
