package com.komainos.inventario.model;

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

/**
 * Dirección IP de un servidor, tabla {@code direccion_ip} (DEC-37).
 *
 * <p>Un servidor tiene una o varias; exactamente una es la principal, la que
 * el sistema usa para identificarlo y conectarse. Una dirección no puede
 * pertenecer a dos servidores (uq_direccion_ip_direccion), lo que mantiene la
 * detección de duplicados por IP al registrar e importar (RF09, R2.4).
 */
@Entity
@Table(name = "direccion_ip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DireccionIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_direccion_ip")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false)
    private Servidor servidor;

    /** IPv4 o IPv6, en minúsculas. */
    @Column(name = "direccion", nullable = false, length = 45)
    private String direccion;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    DireccionIp(Servidor servidor, String direccion, boolean principal) {
        this.servidor = servidor;
        this.direccion = direccion;
        this.principal = principal;
    }

    void marcarPrincipal(boolean principal) {
        this.principal = principal;
    }
}
