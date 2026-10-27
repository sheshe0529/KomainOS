package com.komainos.mantenimiento.model;

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

/** Rol al que se notifica cuando se aplica la regla de la política de cambio (RF43) */
@Entity
@Table(name = "notificacion_regla")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificacionRegla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion_regla")
    private Integer id;

    @Column(name = "id_regla_politica_cambio", nullable = false)
    private Integer idRegla;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "destinatario", nullable = false, columnDefinition = "enum_tipo_destinatario")
    private TipoDestinatario destinatario;

    public static NotificacionRegla de(Integer idRegla, TipoDestinatario destinatario) {
        NotificacionRegla n = new NotificacionRegla();
        n.idRegla = idRegla;
        n.destinatario = destinatario;
        return n;
    }
}
