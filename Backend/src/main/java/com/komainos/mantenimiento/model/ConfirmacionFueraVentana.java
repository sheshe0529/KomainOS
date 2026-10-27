package com.komainos.mantenimiento.model;

import com.komainos.seguridad.model.Usuario;
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

/**
 * Excepción expresa del administrador para iniciar un lanzamiento inmediato fuera de la ventana permisiva (RF31, HU19
 * CA3). Mientras exista, el fin de la ventana no detiene la ejecución (RF60, CP140).
 */
@Entity
@Table(name = "confirmacion_fuera_ventana")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmacionFueraVentana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_confirmacion_fuera_ventana")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_confirmacion", nullable = false)
    private Instant fechaConfirmacion;

    @Column(name = "motivo", nullable = false, length = 1000)
    private String motivo;

    public static ConfirmacionFueraVentana de(Orden orden, Usuario usuario, String motivo, Instant ahora) {
        ConfirmacionFueraVentana c = new ConfirmacionFueraVentana();
        c.orden = orden;
        c.usuario = usuario;
        c.motivo = motivo;
        c.fechaConfirmacion = ahora;
        return c;
    }
}
