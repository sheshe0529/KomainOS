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

@Entity @Table(name="validacion_funcional") @Getter @Setter @NoArgsConstructor
public class ValidacionFuncional {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_validacion_funcional") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden") private Orden orden;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_usuario_responsable") private Usuario responsable;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="estado",columnDefinition="enum_estado_validacion_funcional") private EstadoValidacionFuncional estado=EstadoValidacionFuncional.PENDIENTE;
  @Column(name="fecha_solicitud") private Instant fechaSolicitud;
  @Column(name="fecha_limite") private Instant fechaLimite;
  @Column(name="fecha_respuesta") private Instant fechaRespuesta;
  @Column(name="observaciones") private String observaciones;
 public void responder(EstadoValidacionFuncional decision,String observaciones,Instant ahora){if(estado!=EstadoValidacionFuncional.PENDIENTE)throw new ReglaNegocioException("La validación ya fue respondida");if(decision==EstadoValidacionFuncional.PENDIENTE)throw new ReglaNegocioException("Indique una decisión");estado=decision;this.observaciones=observaciones;fechaRespuesta=ahora;}
}
