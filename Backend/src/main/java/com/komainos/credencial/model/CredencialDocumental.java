package com.komainos.credencial.model;

import com.komainos.inventario.model.Servidor;
import jakarta.persistence.Column;
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

    /** La que viaja en la exportación e importación del inventario, una por servidor (ex_credencial_documental_principal) */
    @Column(name = "principal", nullable = false)
    private boolean principal;

    public static CredencialDocumental nueva(Servidor servidor, String nombre, String usuarioAcceso,
                                             String descripcion, boolean principal, Instant ahora) {
        CredencialDocumental credencial = new CredencialDocumental();
        credencial.servidor = servidor;
        credencial.principal = principal;
        credencial.registrar(nombre, usuarioAcceso, descripcion, ahora);
        return credencial;
    }

    public void marcarPrincipal() {
        exigirVigente("marcarla como principal");
        principal = true;
    }

    public void quitarPrincipal() {
        principal = false;
    }
}
