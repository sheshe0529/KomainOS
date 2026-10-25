package com.komainos.alerta.model;
import jakarta.persistence.*;import lombok.*;import java.time.Instant;import org.hibernate.annotations.JdbcType;import org.hibernate.dialect.PostgreSQLEnumJdbcType;
@Entity @Table(name="alerta") @Getter @Setter @NoArgsConstructor
public class Alerta {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_alerta") private Integer id;
 @Column(name="id_servidor")private Integer idServidor;@Column(name="id_grupo_mantenimiento")private Integer idGrupo;
 @Column(name="id_orden")private Integer idOrden;@Column(name="id_incidencia")private Integer idIncidencia;
 @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(columnDefinition="enum_tipo_alerta")private TipoAlerta tipo;
 @Column(length=255)private String titulo;@Column(length=2000)private String mensaje;@Column(name="fecha_generacion")private Instant fechaGeneracion;
}
