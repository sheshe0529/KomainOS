package com.komainos.mantenimiento.model;

import com.komainos.catalogo.model.VersionScript;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.Servidor;
import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Una ejecución del script de análisis sobre un servidor (RF37, RF38, RF55): conserva la versión del script, la cuenta,
 * las mediciones y los valores de umbral aplicados. Una revisión periódica no pertenece a una orden; la evaluación
 * previa y la posterior sí (ck_evaluacion_detalle_tipo).
 */
@Entity
@Table(name = "evaluacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evaluacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false)
    private Servidor servidor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden_detalle")
    private OrdenDetalle detalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_script_version", nullable = false)
    private VersionScript version;

    @Column(name = "id_cuenta_servicio", nullable = false)
    private Integer idCuentaServicio;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo", nullable = false, columnDefinition = "enum_tipo_evaluacion")
    private TipoEvaluacion tipo;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_evaluacion")
    private EstadoEvaluacion estado;

    @Column(name = "error")
    private String error;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    @Column(name = "fecha_fin")
    private Instant fechaFin;

    @OneToMany(mappedBy = "evaluacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("id ASC")
    private List<MedicionMetrica> mediciones = new ArrayList<>();

    public static Evaluacion iniciar(Servidor servidor, OrdenDetalle detalle, VersionScript version,
                                     Integer idCuentaServicio, TipoEvaluacion tipo, Instant ahora) {
        if ((tipo == TipoEvaluacion.REVISION_PERIODICA) != (detalle == null)) {
            throw new IllegalArgumentException("La revisión periódica no pertenece a una orden; la previa y la posterior sí");
        }
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.servidor = servidor;
        evaluacion.detalle = detalle;
        evaluacion.version = version;
        evaluacion.idCuentaServicio = idCuentaServicio;
        evaluacion.tipo = tipo;
        evaluacion.estado = EstadoEvaluacion.EN_CURSO;
        evaluacion.fechaInicio = ahora;
        return evaluacion;
    }

    public void agregarMedicion(MedicionMetrica medicion) {
        exigirEnCurso();
        mediciones.add(medicion);
    }

    public void finalizar(Instant ahora) {
        exigirEnCurso();
        estado = EstadoEvaluacion.FINALIZADA;
        fechaFin = ahora;
    }

    /** CP103: un análisis sin todas las métricas requeridas no es concluyente y no conserva mediciones parciales */
    public void fallar(String motivo, Instant ahora) {
        exigirEnCurso();
        mediciones.clear();
        estado = EstadoEvaluacion.FALLIDA;
        error = motivo;
        fechaFin = ahora;
    }

    public boolean estaFinalizada() {
        return estado == EstadoEvaluacion.FINALIZADA;
    }

    /** El peor nivel entre todas las mediciones; NORMAL si no hay umbrales evaluados */
    public NivelAlerta nivel() {
        return mediciones.stream()
                .flatMap(m -> m.getUmbrales().stream())
                .map(EvaluacionUmbral::getNivel)
                .reduce(NivelAlerta.NORMAL, NivelAlerta::peor);
    }

    /** Umbrales en advertencia o crítico: sus tareas forman la MOP (RF41) */
    public Set<Integer> umbralesActivados() {
        Set<Integer> ids = new LinkedHashSet<>();
        mediciones.forEach(m -> m.getUmbrales().stream()
                .filter(u -> u.getNivel() != NivelAlerta.NORMAL)
                .forEach(u -> ids.add(u.getIdUmbral())));
        return ids;
    }

    private void exigirEnCurso() {
        if (estado != EstadoEvaluacion.EN_CURSO) {
            throw new ReglaNegocioException("La evaluación ya terminó");
        }
    }
}
