package com.komainos.credencial.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

/** Inmutable: las evaluaciones y las tareas de MOP referencian la versión con que se autenticaron */
@Entity
@Table(name = "credencial_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredencialVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credencial_version")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_credencial", nullable = false, updatable = false)
    private Credencial credencial;

    @Column(name = "numero_version", nullable = false, updatable = false)
    private int numeroVersion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_autenticacion", nullable = false, updatable = false, columnDefinition = "enum_tipo_autenticacion")
    private TipoAutenticacion tipoAutenticacion;

    // Sin getters: el material cifrado solo sale como SecretoCifrado hacia el cifrador
    @Getter(AccessLevel.NONE)
    @Column(name = "secreto_cifrado", nullable = false, updatable = false)
    private byte[] secretoCifrado;

    @Getter(AccessLevel.NONE)
    @Column(name = "iv_nonce", nullable = false, updatable = false)
    private byte[] ivNonce;

    @Getter(AccessLevel.NONE)
    @Column(name = "tag_autenticacion", nullable = false, updatable = false)
    private byte[] tagAutenticacion;

    @Column(name = "algoritmo", nullable = false, updatable = false, length = 100)
    private String algoritmo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    static CredencialVersion nueva(Credencial credencial, int numero, TipoAutenticacion tipo, SecretoCifrado secreto,
                                   Instant ahora) {
        CredencialVersion version = new CredencialVersion();
        version.credencial = credencial;
        version.numeroVersion = numero;
        version.tipoAutenticacion = tipo;
        version.secretoCifrado = secreto.cifrado();
        version.ivNonce = secreto.iv();
        version.tagAutenticacion = secreto.tag();
        version.algoritmo = secreto.algoritmo();
        version.fechaCreacion = ahora;
        return version;
    }

    public SecretoCifrado secreto() {
        return new SecretoCifrado(secretoCifrado, ivNonce, tagAutenticacion, algoritmo);
    }
}
