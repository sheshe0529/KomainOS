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

@Entity @Table(name="autorizacion_detalle") @Getter @Setter @NoArgsConstructor
public class AutorizacionDetalle {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_autorizacion_detalle") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_solicitud_autorizacion") private SolicitudAutorizacion solicitud;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_usuario_decision") private Usuario usuarioDecision;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="tipo_aprobador",columnDefinition="enum_tipo_aprobador") private TipoAprobador tipo;
  @Column(name="orden_secuencia") private Integer ordenSecuencia;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="estado",columnDefinition="enum_estado_autorizacion_detalle") private EstadoAutorizacion estado=EstadoAutorizacion.PENDIENTE;
  @Column(name="fecha_solicitud") private Instant fechaSolicitud;
  @Column(name="fecha_decision") private Instant fechaDecision;
  @Column(name="fecha_propuesta") private Instant fechaPropuesta;
  @Column(name="motivo") private String motivo;
 public void definirTipo(TipoAprobador tipo){this.tipo=tipo;}
 public void decidir(EstadoAutorizacion destino,Usuario autor,String motivo,Instant propuesta,Instant ahora){if(estado!=EstadoAutorizacion.PENDIENTE)throw new ReglaNegocioException("Esta autorización ya fue respondida");if(destino==EstadoAutorizacion.PENDIENTE)throw new ReglaNegocioException("Indique una decisión");estado=destino;usuarioDecision=autor;this.motivo=motivo;fechaPropuesta=propuesta;fechaDecision=ahora;}
}
