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

@Entity @Table(name="mop") @Getter @Setter @NoArgsConstructor
public class Mop {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_mop") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_orden_detalle") private OrdenDetalle detalle;
  @Column(name="fecha_generacion") private Instant fechaGeneracion;
 @OneToMany(mappedBy="mop",cascade=CascadeType.ALL) @OrderBy("ordenEjecucion ASC") private List<MopTarea> tareas=new ArrayList<>();
 public int duracionTotal(){return tareas.stream().mapToInt(MopTarea::getDuracionEstimadaMinutos).sum();}
}
