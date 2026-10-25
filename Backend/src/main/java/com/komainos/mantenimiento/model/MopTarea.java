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

@Entity @Table(name="mop_tarea") @Getter @Setter @NoArgsConstructor
public class MopTarea {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_mop_tarea") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_mop") private Mop mop;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_tarea") private Tarea tarea;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_script_version") private VersionScript version;
  @Column(name="id_cuenta_servicio") private Integer idCuentaServicio;
  @Column(name="orden_ejecucion") private Integer ordenEjecucion;
  @Column(name="prioridad_aplicada") private Integer prioridadAplicada;
  @Column(name="duracion_estimada_minutos") private Integer duracionEstimadaMinutos;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="nivel_afectacion_aplicado",columnDefinition="enum_nivel_afectacion") private NivelAfectacion nivelAfectacion;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="estado",columnDefinition="enum_estado_tarea_mop") private EstadoTareaMop estado=EstadoTareaMop.PENDIENTE;
  @Column(name="codigo_retorno") private Integer codigoRetorno;
  @Column(name="salida") private String salida;
  @Column(name="error") private String error;
  @Column(name="fecha_inicio") private Instant fechaInicio;
  @Column(name="fecha_fin") private Instant fechaFin;
 public void fijarAfectacion(NivelAfectacion n){nivelAfectacion=n;}
 public void iniciar(Instant ahora){if(estado!=EstadoTareaMop.PENDIENTE)throw new ReglaNegocioException("La tarea ya inició");estado=EstadoTareaMop.EN_EJECUCION;fechaInicio=ahora;}
 public void terminar(int codigo,String salida,String error,Instant ahora){if(estado!=EstadoTareaMop.EN_EJECUCION)throw new ReglaNegocioException("La tarea no está en ejecución");codigoRetorno=codigo;this.salida=salida;this.error=error;fechaFin=ahora;estado=codigo==0?EstadoTareaMop.FINALIZADA:EstadoTareaMop.FALLIDA;}
 public void omitir(){if(estado==EstadoTareaMop.PENDIENTE)estado=EstadoTareaMop.NO_INICIADA;}
}
