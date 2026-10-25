package com.komainos.catalogo.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import com.komainos.inventario.model.TipoDato;
@Entity @Table(name="script_parametro") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ParametroScript {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_script_parametro") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_script_version") private VersionScript version;
 @Column(nullable=false,length=255) private String nombre;
 @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="tipo_dato",nullable=false,columnDefinition="enum_tipo_dato_parametro") private TipoDato tipoDato;
 @Column(nullable=false) private boolean obligatorio;
 @Column(name="valor_por_defecto",length=1000) private String valorPorDefecto;
 @Column(nullable=false) private Integer orden;
 public static ParametroScript nuevo(VersionScript v,String nombre,TipoDato tipo,boolean obligatorio,String defecto,int orden) {var p=new ParametroScript();p.version=v;p.nombre=nombre;p.tipoDato=tipo;p.obligatorio=obligatorio;p.valorPorDefecto=defecto;p.orden=orden;return p;}
}
