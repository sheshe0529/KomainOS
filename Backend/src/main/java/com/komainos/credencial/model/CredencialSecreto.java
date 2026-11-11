package com.komainos.credencial.model;

import com.komainos.ejecucion.model.MetodoElevacion;
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

    /** Cómo obtiene privilegios Ansible después de conectarse (DEC-57) */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "metodo_elevacion", nullable = false, columnDefinition = "enum_metodo_elevacion")
    private MetodoElevacion metodoElevacion;

    /** Contraseña de sudo o de root para su, cifrada con su propio IV; nula con sudo sin contraseña (DEC-57) */
    @Getter(AccessLevel.NONE)
    @Column(name = "elevacion_secreto_cifrado")
    private byte[] elevacionSecretoCifrado;

    @Getter(AccessLevel.NONE)
    @Column(name = "elevacion_iv_nonce")
    private byte[] elevacionIvNonce;

    @Getter(AccessLevel.NONE)
    @Column(name = "elevacion_tag_autenticacion")
    private byte[] elevacionTagAutenticacion;

    /** Registro o última rotación del secreto */
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    static CredencialSecreto nuevo(Credencial credencial) {
        CredencialSecreto secreto = new CredencialSecreto();
        secreto.credencial = credencial;
        return secreto;
    }

    /** Reemplaza todo el material: un método distinto no arrastra la contraseña de elevación del anterior */
    void reemplazar(TipoAutenticacion tipo, MetodoElevacion metodo, SecretoCifrado secreto, SecretoCifrado elevacion,
                    Instant ahora) {
        this.tipoAutenticacion = tipo;
        this.metodoElevacion = metodo;
        this.secretoCifrado = secreto.cifrado();
        this.ivNonce = secreto.iv();
        this.tagAutenticacion = secreto.tag();
        this.algoritmo = secreto.algoritmo();
        this.elevacionSecretoCifrado = elevacion == null ? null : elevacion.cifrado();
        this.elevacionIvNonce = elevacion == null ? null : elevacion.iv();
        this.elevacionTagAutenticacion = elevacion == null ? null : elevacion.tag();
        this.fechaActualizacion = ahora;
    }

    public SecretoCifrado secreto() {
        return new SecretoCifrado(secretoCifrado, ivNonce, tagAutenticacion, algoritmo);
    }

    public Optional<SecretoCifrado> secretoElevacion() {
        return elevacionSecretoCifrado == null ? Optional.empty()
                : Optional.of(new SecretoCifrado(elevacionSecretoCifrado, elevacionIvNonce, elevacionTagAutenticacion,
                algoritmo));
    }

    public boolean tieneContrasenaElevacion() {
        return elevacionSecretoCifrado != null;
    }
}
