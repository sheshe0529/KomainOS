package com.komainos.credencial.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.Optional;

/** Secreto vigente de la credencial, con su mismo id: al rotarlo se sobrescribe sin conservar el anterior (R2.4) */
@Entity
@Table(name = "credencial_secreto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredencialSecreto {

    @Id
    @Column(name = "id_credencial")
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_credencial")
    private Credencial credencial;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_autenticacion", nullable = false, columnDefinition = "enum_tipo_autenticacion")
    private TipoAutenticacion tipoAutenticacion;

    // Sin getters: el material cifrado solo sale como SecretoCifrado hacia el cifrador
    @Getter(AccessLevel.NONE)
    @Column(name = "secreto_cifrado", nullable = false)
    private byte[] secretoCifrado;

    @Getter(AccessLevel.NONE)
    @Column(name = "iv_nonce", nullable = false)
    private byte[] ivNonce;

    @Getter(AccessLevel.NONE)
    @Column(name = "tag_autenticacion", nullable = false)
    private byte[] tagAutenticacion;

    @Column(name = "algoritmo", nullable = false, length = 100)
    private String algoritmo;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_usuario", columnDefinition = "enum_tipo_usuario")
    private TipoUsuario tipoUsuario;

    /** Contraseña de root para su, cifrada con su propio IV (DEC-39) */
    @Getter(AccessLevel.NONE)
    @Column(name = "su_secreto_cifrado")
    private byte[] suSecretoCifrado;

    @Getter(AccessLevel.NONE)
    @Column(name = "su_iv_nonce")
    private byte[] suIvNonce;

    @Getter(AccessLevel.NONE)
    @Column(name = "su_tag_autenticacion")
    private byte[] suTagAutenticacion;

    /** Registro o última rotación del secreto */
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    static CredencialSecreto nuevo(Credencial credencial) {
        CredencialSecreto secreto = new CredencialSecreto();
        secreto.credencial = credencial;
        return secreto;
    }

    /** Reemplaza todo el material: un mecanismo distinto no arrastra la contraseña su del anterior */
    void reemplazar(TipoAutenticacion tipo, TipoUsuario tipoUsuario, SecretoCifrado secreto, SecretoCifrado su,
                    Instant ahora) {
        this.tipoAutenticacion = tipo;
        this.tipoUsuario = tipoUsuario;
        this.secretoCifrado = secreto.cifrado();
        this.ivNonce = secreto.iv();
        this.tagAutenticacion = secreto.tag();
        this.algoritmo = secreto.algoritmo();
        this.suSecretoCifrado = su == null ? null : su.cifrado();
        this.suIvNonce = su == null ? null : su.iv();
        this.suTagAutenticacion = su == null ? null : su.tag();
        this.fechaActualizacion = ahora;
    }

    public SecretoCifrado secreto() {
        return new SecretoCifrado(secretoCifrado, ivNonce, tagAutenticacion, algoritmo);
    }

    public Optional<SecretoCifrado> secretoSu() {
        return suSecretoCifrado == null ? Optional.empty()
                : Optional.of(new SecretoCifrado(suSecretoCifrado, suIvNonce, suTagAutenticacion, algoritmo));
    }

    public boolean tieneSu() {
        return suSecretoCifrado != null;
    }
}
