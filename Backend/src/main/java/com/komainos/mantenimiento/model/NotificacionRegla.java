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

@Entity @Table(name="notificacion_regla") @Getter @Setter @NoArgsConstructor
public class NotificacionRegla {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_notificacion_regla") private Integer id;
  @Column(name="id_regla_politica_cambio") private Integer idRegla;
 @Setter(AccessLevel.NONE) @Enumerated(EnumType.STRING) @JdbcType(PostgreSQLEnumJdbcType.class) @Column(name="destinatario",columnDefinition="enum_tipo_destinatario") private TipoDestinatario destinatario;
 public void definirDestinatario(TipoDestinatario t){destinatario=t;}
}
