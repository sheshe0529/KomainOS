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

@Entity @Table(name="regla_politica_cambio") @Getter @Setter @NoArgsConstructor
public class ReglaPoliticaCambio {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_regla_politica_cambio") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_entorno") private Entorno entorno;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="nivel_afectacion",columnDefinition="enum_nivel_afectacion") private NivelAfectacion afectacion;
  @Column(name="requiere_aprobacion_responsable") private boolean requiereResponsable;
  @Column(name="requiere_aprobacion_administrador") private boolean requiereAdministrador;
  @Column(name="activo") private boolean activo=true;
  @Column(name="fecha_actualizacion") private Instant fechaActualizacion;
 public void definirAfectacion(NivelAfectacion n){afectacion=n;}
}
