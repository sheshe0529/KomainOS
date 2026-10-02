package com.komainos.credencial.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Credencial con la que Ansible se conecta a los servidores para ejecutar el mantenimiento (RF05) */
@Entity
@Table(name = "cuenta_servicio")
@PrimaryKeyJoinColumn(name = "id_credencial")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CuentaServicio extends Credencial {

    public static CuentaServicio nueva(String nombre, String usuarioAcceso, String descripcion, Instant ahora) {
        CuentaServicio cuenta = new CuentaServicio();
        cuenta.registrar(nombre, usuarioAcceso, descripcion, ahora);
        return cuenta;
    }
}
