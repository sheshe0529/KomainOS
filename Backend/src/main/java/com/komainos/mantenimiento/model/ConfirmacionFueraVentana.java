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

@Entity @Table(name="confirmacion_fuera_ventana") @Getter @Setter @NoArgsConstructor
public class ConfirmacionFueraVentana {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_confirmacion_fuera_ventana") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden") private Orden orden;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_usuario") private Usuario usuario;
  @Column(name="fecha_confirmacion") private Instant fechaConfirmacion;
  @Column(name="motivo") private String motivo;
 
}
