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

@Entity @Table(name="incidencia") @Getter @Setter @NoArgsConstructor
public class Incidencia {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_incidencia") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden_detalle") private OrdenDetalle detalle;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_usuario_atencion") private Usuario usuarioAtencion;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="origen",columnDefinition="enum_origen_incidencia") private OrigenIncidencia origen;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="estado",columnDefinition="enum_estado_incidencia") private EstadoIncidencia estado=EstadoIncidencia.ABIERTA;
  @Column(name="error") private String error;
  @Column(name="diagnostico") private String diagnostico;
  @Column(name="acciones_realizadas") private String accionesRealizadas;
  @Column(name="resultado") private String resultado;
  @Column(name="fecha_apertura") private Instant fechaApertura;
  @Column(name="fecha_cierre") private Instant fechaCierre;
 public void definirOrigen(OrigenIncidencia o){origen=o;}
 public void atender(Usuario autor,String diagnostico,String acciones,String resultado,boolean cerrar,Instant ahora){if(estado==EstadoIncidencia.CERRADA)throw new ReglaNegocioException("La incidencia ya está cerrada");usuarioAtencion=autor;this.diagnostico=diagnostico;accionesRealizadas=acciones;this.resultado=resultado;estado=cerrar?EstadoIncidencia.CERRADA:EstadoIncidencia.EN_ATENCION;fechaCierre=cerrar?ahora:null;}
}
