package com.komainos.mantenimiento.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Una ronda de autorización de la orden (RF43-RF50, RF75): las decisiones que exige la regla aplicada, en secuencia y
 * con un único plazo compartido. Una ronda sin decisiones es una autorización automática. Aceptar una fecha propuesta
 * abre una ronda nueva; las anteriores se conservan como historial.
 */
@Entity
@Table(name = "solicitud_autorizacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitudAutorizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_autorizacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_regla_politica_cambio", nullable = false)
    private ReglaPoliticaCambio regla;

    @Column(name = "numero_ronda", nullable = false)
    private Integer numeroRonda;

    @Column(name = "fecha_limite", nullable = false)
    private Instant fechaLimite;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @OrderBy("ordenSecuencia ASC")
    private List<AutorizacionDetalle> decisiones = new ArrayList<>();

    /** RF44: si la regla exige ambos, primero el responsable y después el administrador */
    public static SolicitudAutorizacion abrir(Orden orden, ReglaPoliticaCambio regla, int ronda, Instant limite,
                                              Instant ahora) {
        SolicitudAutorizacion s = new SolicitudAutorizacion();
        s.orden = orden;
        s.regla = regla;
        s.numeroRonda = ronda;
        s.fechaLimite = limite;
        if (regla.isRequiereResponsable()) {
            s.decisiones.add(AutorizacionDetalle.nueva(s, TipoAprobador.RESPONSABLE, s.decisiones.size() + 1, ahora));
        }
        if (regla.isRequiereAdministrador()) {
            s.decisiones.add(AutorizacionDetalle.nueva(s, TipoAprobador.ADMINISTRADOR, s.decisiones.size() + 1, ahora));
        }
        return s;
    }

    public boolean esAutomatica() {
        return decisiones.isEmpty();
    }

    /** La única decisión que puede responderse ahora: la primera pendiente de la secuencia */
    public Optional<AutorizacionDetalle> pendienteActual() {
        if (propuesta().isPresent()) {
            return Optional.empty();
        }
        return decisiones.stream().filter(AutorizacionDetalle::estaPendiente).findFirst();
    }

    public Optional<AutorizacionDetalle> propuesta() {
        return decisiones.stream().filter(d -> d.getEstado() == EstadoAutorizacion.FECHA_PROPUESTA).findFirst();
    }

    public boolean aprobada() {
        return decisiones.stream().allMatch(d -> d.getEstado() == EstadoAutorizacion.APROBADA);
    }

    /** RF75: el plazo es compartido; vence al alcanzar la fecha límite */
    public boolean vencida(Instant ahora) {
        return !ahora.isBefore(fechaLimite);
    }

    public void vencer(Instant ahora) {
        decisiones.stream().filter(AutorizacionDetalle::estaPendiente).forEach(d -> d.vencer(ahora));
    }
}
