package com.komainos.alerta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

/**
 * Evento notificado (RF69): tipo, momento y a qué servidor, grupo, orden o incidencia se refiere. El mensaje nunca
 * incluye secretos (HU30 CA4). Los destinatarios y su lectura van en {@link AlertaDestinatario}.
 */
@Entity
@Table(name = "alerta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Integer id;

    @Column(name = "id_servidor")
    private Integer idServidor;

    @Column(name = "id_grupo_mantenimiento")
    private Integer idGrupo;

    @Column(name = "id_orden")
    private Integer idOrden;

    @Column(name = "id_incidencia")
    private Integer idIncidencia;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo", nullable = false, columnDefinition = "enum_tipo_alerta")
    private TipoAlerta tipo;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    @Column(name = "mensaje", nullable = false, length = 2000)
    private String mensaje;

    @Column(name = "fecha_generacion", nullable = false)
    private Instant fechaGeneracion;

    public static Alerta nueva(TipoAlerta tipo, String titulo, String mensaje, Integer idServidor, Integer idGrupo,
                               Integer idOrden, Integer idIncidencia, Instant ahora) {
        Alerta alerta = new Alerta();
        alerta.tipo = tipo;
        alerta.titulo = recortar(titulo, 255);
        alerta.mensaje = recortar(mensaje == null || mensaje.isBlank() ? titulo : mensaje, 2000);
        alerta.idServidor = idServidor;
        alerta.idGrupo = idGrupo;
        alerta.idOrden = idOrden;
        alerta.idIncidencia = idIncidencia;
        alerta.fechaGeneracion = ahora;
        return alerta;
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }
}
