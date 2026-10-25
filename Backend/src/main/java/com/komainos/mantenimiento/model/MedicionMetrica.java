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

@Entity @Table(name="medicion_metrica") @Getter @Setter @NoArgsConstructor
public class MedicionMetrica {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_medicion_metrica") private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_evaluacion") private Evaluacion evaluacion;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_metrica") private Metrica metrica;
  @Column(name="valor") private BigDecimal valor;
  @Column(name="instancia") private String instancia="";
 @ElementCollection @CollectionTable(name="medicion_lista_item",joinColumns=@JoinColumn(name="id_medicion_metrica")) @Column(name="valor",length=255) private Set<String> elementos=new LinkedHashSet<>();
 @OneToMany(mappedBy="medicion",cascade=CascadeType.ALL) @OrderBy("id ASC") private List<EvaluacionUmbral> umbrales=new ArrayList<>();
 
}
