package com.komainos.catalogo.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/** Catálogo lógico del script; el código se publica exclusivamente como versiones nuevas (RF24-RF26). */
@Entity @Table(name="script") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Script {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_script") private Integer id;
 @Column(nullable=false,length=255) private String nombre;
 @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(nullable=false,columnDefinition="enum_tipo_script") private TipoScript tipo;
 @Column(length=500) private String descripcion;
 @Column(nullable=false) private boolean activo=true;
 @OneToMany(mappedBy="script",cascade=CascadeType.ALL) @OrderBy("id ASC") private List<ImplementacionScript> implementaciones=new ArrayList<>();
 public static Script nuevo(String nombre, TipoScript tipo, String descripcion) { Script s=new Script(); s.nombre=nombre.trim(); s.tipo=tipo; s.descripcion=descripcion; return s; }
 public void actualizar(String nombre,String descripcion,boolean activo) { this.nombre=nombre.trim(); this.descripcion=descripcion; this.activo=activo; }
}
