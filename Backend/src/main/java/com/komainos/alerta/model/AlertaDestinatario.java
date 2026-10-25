package com.komainos.alerta.model;
import jakarta.persistence.*;import lombok.*;import java.time.Instant;
@Entity @Table(name="alerta_destinatario") @Getter @NoArgsConstructor
public class AlertaDestinatario {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_alerta_destinatario")private Integer id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_alerta")private Alerta alerta;
 @Column(name="id_usuario")private Integer idUsuario;private boolean leida;
 @Column(name="fecha_lectura")private Instant fechaLectura;
 public static AlertaDestinatario nuevo(Alerta a,Integer usuario){var d=new AlertaDestinatario();d.alerta=a;d.idUsuario=usuario;return d;}
 public void leer(Instant ahora){leida=true;fechaLectura=ahora;}
}
