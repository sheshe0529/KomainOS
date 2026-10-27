package com.komainos.alerta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Una alerta para un usuario: cada destinatario la lee por su cuenta (HU30 CA3) */
@Entity
@Table(name = "alerta_destinatario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaDestinatario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta_destinatario")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alerta", nullable = false)
    private Alerta alerta;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    @Column(name = "fecha_lectura")
    private Instant fechaLectura;

    public static AlertaDestinatario nuevo(Alerta alerta, Integer idUsuario) {
        AlertaDestinatario destinatario = new AlertaDestinatario();
        destinatario.alerta = alerta;
        destinatario.idUsuario = idUsuario;
        return destinatario;
    }

    /** La primera lectura queda registrada: volver a abrirla no cambia la fecha */
    public void leer(Instant ahora) {
        if (!leida) {
            leida = true;
            fechaLectura = ahora;
        }
    }
}
