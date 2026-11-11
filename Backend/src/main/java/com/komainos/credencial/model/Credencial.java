package com.komainos.credencial.model;

import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.Optional;

/** Identidad común de la credencial, sin secretos: el vigente vive cifrado en credencial_secreto (R2.4) */
@Entity
@Table(name = "credencial")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Credencial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credencial")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "usuario_acceso", nullable = false, length = 255)
    private String usuarioAcceso;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "estado", nullable = false, columnDefinition = "enum_estado_credencial")
    private EstadoCredencial estado;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @Column(name = "fecha_revocacion")
    private Instant fechaRevocacion;

    /**
     * Como máximo uno: rotarlo lo sobrescribe y revocar la credencial lo elimina (RF04). Sin orphanRemoval: en el lado
     * inverso de un @OneToOne con @MapsId Hibernate no lo aplica, el servicio borra el secreto al revocar.
     */
    @Getter(AccessLevel.NONE)
    @OneToOne(mappedBy = "credencial", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private CredencialSecreto secreto;

    protected void registrar(String nombre, String usuarioAcceso, String descripcion, Instant ahora) {
        aplicarDatos(nombre, usuarioAcceso, descripcion);
        this.estado = EstadoCredencial.VIGENTE;
        this.fechaRegistro = ahora;
    }

    public void actualizarDatos(String nombre, String usuarioAcceso, String descripcion) {
        exigirVigente("editarla");
        aplicarDatos(nombre, usuarioAcceso, descripcion);
    }

    /**
     * Registra o rota el secreto. Igual que ck_credencial_secreto_elevacion: su exige la contraseña de root, sudo la
     * admite y ninguno o runas no guardan contraseña de elevación (DEC-57)
     */
    public CredencialSecreto establecerSecreto(TipoAutenticacion tipo, MetodoElevacion metodo, SecretoCifrado cifrado,
                                               SecretoCifrado elevacion, Instant ahora) {
        exigirVigente("actualizar su secreto");
        if (metodo == null) {
            throw new ReglaNegocioException("Indique el método de elevación de privilegios");
        }
        if (metodo.exigeContrasena() && elevacion == null) {
            throw new ReglaNegocioException("La elevación con su requiere la contraseña de root");
        }
        if (!metodo.admiteContrasena() && elevacion != null) {
            throw new ReglaNegocioException("La elevación %s no lleva contraseña de elevación".formatted(metodo.etiqueta()));
        }
        if (secreto == null) {
            secreto = CredencialSecreto.nuevo(this);
        }
        secreto.reemplazar(tipo, metodo, cifrado, elevacion, ahora);
        return secreto;
    }

    /** Suelta el secreto, que quien llama elimina: las ejecuciones conservan la cuenta de servicio, no el secreto */
    public void revocar(Instant ahora) {
        if (!estaVigente()) {
            throw new ReglaNegocioException("La credencial %s ya está revocada".formatted(nombre));
        }
        estado = EstadoCredencial.REVOCADA;
        fechaRevocacion = ahora;
        secreto = null;
    }

    public boolean estaVigente() {
        return estado == EstadoCredencial.VIGENTE;
    }

    public void exigirVigente(String accion) {
        if (!estaVigente()) {
            throw new ReglaNegocioException("La credencial %s está revocada: no se puede %s".formatted(nombre, accion));
        }
    }

    public Optional<CredencialSecreto> secretoVigente() {
        return Optional.ofNullable(secreto);
    }

    private void aplicarDatos(String nombre, String usuarioAcceso, String descripcion) {
        this.nombre = nombre;
        this.usuarioAcceso = usuarioAcceso;
        this.descripcion = descripcion;
    }
}
