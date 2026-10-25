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

@Entity @Table(name="solicitud_autorizacion") @Getter @Setter @NoArgsConstructor
public class SolicitudAutorizacion {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_solicitud_autorizacion") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden") private Orden orden;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_regla_politica_cambio") private ReglaPoliticaCambio regla;
  @Column(name="numero_ronda") private Integer numeroRonda;
  @Column(name="fecha_limite") private Instant fechaLimite;
 @OneToMany(mappedBy="solicitud",cascade=CascadeType.ALL) @OrderBy("ordenSecuencia ASC") private List<AutorizacionDetalle> decisiones=new ArrayList<>();
 
}
