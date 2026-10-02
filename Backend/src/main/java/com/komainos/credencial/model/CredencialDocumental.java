package com.komainos.credencial.model;

import com.komainos.inventario.model.Servidor;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Documenta un acceso al servidor como parte del inventario: la ejecución no la usa (R2.4) */
@Entity
@Table(name = "credencial_documental")
@PrimaryKeyJoinColumn(name = "id_credencial")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredencialDocumental extends Credencial {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false, updatable = false)
    private Servidor servidor;

    public static CredencialDocumental nueva(Servidor servidor, String nombre, String usuarioAcceso,
                                             String descripcion, Instant ahora) {
        CredencialDocumental credencial = new CredencialDocumental();
        credencial.servidor = servidor;
        credencial.registrar(nombre, usuarioAcceso, descripcion, ahora);
        return credencial;
    }
}
