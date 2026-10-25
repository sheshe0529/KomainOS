package com.komainos.catalogo.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity @Table(name="script_version") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class VersionScript {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_script_version") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_script_implementacion") private ImplementacionScript implementacion;
 @Column(name="numero_version",nullable=false,updatable=false) private Integer numeroVersion;
 @Column(nullable=false,updatable=false,columnDefinition="text") private String contenido;
 @Column(name="notas_cambio",length=1000,updatable=false) private String notasCambio;
 @Column(nullable=false) private boolean habilitada=true;
 @Column(name="fecha_creacion",nullable=false,updatable=false) private Instant fechaCreacion;
 @OneToMany(mappedBy="version",cascade=CascadeType.ALL) @OrderBy("orden ASC") private List<ParametroScript> parametros=new ArrayList<>();
 public static VersionScript nueva(ImplementacionScript i,int numero,String contenido,String notas,Instant ahora) { var v=new VersionScript();v.implementacion=i;v.numeroVersion=numero;v.contenido=contenido;v.notasCambio=notas;v.fechaCreacion=ahora;return v; }
 public void habilitar(boolean valor) { habilitada=valor; }
}
