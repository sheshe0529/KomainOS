package com.komainos.catalogo.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity @Table(name="tarea") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Tarea {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_tarea") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_script") private Script script;
 @Column(nullable=false,length=255) private String nombre;
 @Column(length=500) private String descripcion;
 @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="nivel_afectacion",nullable=false,columnDefinition="enum_nivel_afectacion") private NivelAfectacion nivelAfectacion;
 @Column(nullable=false) private Integer prioridad;
 @Column(name="duracion_estimada_minutos",nullable=false) private Integer duracionEstimadaMinutos;
 @Column(nullable=false) private boolean activo=true;
 @ElementCollection @CollectionTable(name="umbral_tarea",joinColumns=@JoinColumn(name="id_tarea")) @Column(name="id_umbral") private Set<Integer> idsUmbrales=new LinkedHashSet<>();
 public static Tarea nueva() { return new Tarea(); }
 public void actualizar(Script script,String nombre,String descripcion,NivelAfectacion nivel,int prioridad,int duracion,boolean activo,Set<Integer> umbrales) { this.script=script;this.nombre=nombre.trim();this.descripcion=descripcion;this.nivelAfectacion=nivel;this.prioridad=prioridad;this.duracionEstimadaMinutos=duracion;this.activo=activo;idsUmbrales.clear();idsUmbrales.addAll(umbrales); }
}
