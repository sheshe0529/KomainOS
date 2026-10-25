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

@Entity @Table(name="validacion_tecnica") @Getter @Setter @NoArgsConstructor
public class ValidacionTecnica {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_validacion_tecnica") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden_detalle") private OrdenDetalle detalle;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="resultado",columnDefinition="enum_resultado_validacion_tecnica") private ResultadoValidacionTecnica resultado;
  @Column(name="observaciones") private String observaciones;
  @Column(name="fecha_validacion") private Instant fechaValidacion;
 public void definirResultado(ResultadoValidacionTecnica r){resultado=r;}
}
