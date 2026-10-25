package com.komainos.catalogo.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import com.komainos.inventario.model.VersionSistemaOperativo;
@Entity @Table(name="script_implementacion") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ImplementacionScript {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_script_implementacion") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_script") private Script script;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="id_version_sistema_operativo") private VersionSistemaOperativo versionSistemaOperativo;
 @Column(name="fecha_creacion",nullable=false) private Instant fechaCreacion;
 @OneToMany(mappedBy="implementacion",cascade=CascadeType.ALL) @OrderBy("numeroVersion DESC") private List<VersionScript> versiones=new ArrayList<>();
 public static ImplementacionScript nueva(Script script,VersionSistemaOperativo so,Instant ahora) { var i=new ImplementacionScript(); i.script=script;i.versionSistemaOperativo=so;i.fechaCreacion=ahora;return i; }
 public Optional<VersionScript> ultimaHabilitada() { return versiones.stream().filter(VersionScript::isHabilitada).max(Comparator.comparing(VersionScript::getNumeroVersion)); }
}
