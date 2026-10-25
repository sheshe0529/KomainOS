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

@Entity @Table(name="evaluacion_umbral") @Getter @Setter @NoArgsConstructor
public class EvaluacionUmbral {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_evaluacion_umbral") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_medicion_metrica") private MedicionMetrica medicion;
  @Column(name="id_umbral") private Integer idUmbral;
  @Column(name="id_umbral_excepcion") private Integer idExcepcion;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="operador_aplicado",columnDefinition="enum_operador_comparacion") private OperadorComparacion operador;
  @Column(name="valor_advertencia_aplicado") private BigDecimal advertencia;
  @Column(name="valor_critico_aplicado") private BigDecimal critico;
  @Column(name="id_lista_referencia_aplicada") private Integer idLista;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="nivel_faltante_aplicado",columnDefinition="enum_nivel_alerta") private NivelAlerta nivelFaltante;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="nivel_sobrante_aplicado",columnDefinition="enum_nivel_alerta") private NivelAlerta nivelSobrante;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="nivel",columnDefinition="enum_nivel_alerta") private NivelAlerta nivel;
  @Column(name="fecha_evaluacion") private Instant fechaEvaluacion;
 public void aplicar(UmbralAplicado a,NivelAlerta resultado,Instant ahora){idUmbral=a.umbral().getId();idExcepcion=a.excepcion()==null?null:a.excepcion().getId();operador=a.operador();advertencia=a.advertencia();critico=a.critico();idLista=a.lista()==null?null:a.lista().getId();nivelFaltante=a.nivelFaltante();nivelSobrante=a.nivelSobrante();nivel=resultado;fechaEvaluacion=ahora;}
}
