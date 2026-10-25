package com.komainos.mantenimiento.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import com.komainos.inventario.model.*;
import com.komainos.catalogo.model.*;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.exception.ReglaNegocioException;

@Entity @Table(name="evaluacion") @Getter @Setter @NoArgsConstructor
public class Evaluacion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_evaluacion") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_servidor") private Servidor servidor;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden_detalle") private OrdenDetalle detalle;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_script_version") private VersionScript version;
  @Column(name="id_cuenta_servicio") private Integer idCuentaServicio;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="tipo",columnDefinition="enum_tipo_evaluacion") private TipoEvaluacion tipo;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="estado",columnDefinition="enum_estado_evaluacion") private EstadoEvaluacion estado=EstadoEvaluacion.EN_CURSO;
  @Column(name="error") private String error;
  @Column(name="fecha_inicio") private Instant fechaInicio;
  @Column(name="fecha_fin") private Instant fechaFin;
 @OneToMany(mappedBy="evaluacion",cascade=CascadeType.ALL) @OrderBy("id ASC") private List<MedicionMetrica> mediciones=new ArrayList<>();
 public void definirTipo(TipoEvaluacion t){if(id!=null)throw new ReglaNegocioException("La evaluación ya está registrada");tipo=t;}
 public void terminar(String error,Instant ahora){if(estado!=EstadoEvaluacion.EN_CURSO)throw new ReglaNegocioException("La evaluación ya terminó");this.error=error;fechaFin=ahora;estado=error==null?EstadoEvaluacion.FINALIZADA:EstadoEvaluacion.FALLIDA;}
}
