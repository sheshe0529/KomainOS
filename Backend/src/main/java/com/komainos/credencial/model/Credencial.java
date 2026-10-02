package com.komainos.credencial.model;

import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Identidad común de la credencial, sin secretos: estos viven cifrados en sus versiones (R2.4) */
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

    /** Actualizar el secreto agrega una versión y la vigente es la de mayor número (RF04) */
    @OneToMany(mappedBy = "credencial", cascade = CascadeType.PERSIST)
    @OrderBy("numeroVersion ASC")
    private List<CredencialVersion> versiones = new ArrayList<>();

    protected void registrar(String nombre, String usuarioAcceso, String descripcion, Instant ahora) {
        aplicarDatos(nombre, usuarioAcceso, descripcion);
        this.estado = EstadoCredencial.VIGENTE;
        this.fechaRegistro = ahora;
    }

    public void actualizarDatos(String nombre, String usuarioAcceso, String descripcion) {
        exigirVigente("editarla");
        aplicarDatos(nombre, usuarioAcceso, descripcion);
    }

    /** La contraseña su acompaña solo al usuario Genérico, igual que exige ck_credencial_version_su */
    public CredencialVersion agregarVersion(TipoAutenticacion tipo, TipoUsuario tipoUsuario, SecretoCifrado secreto,
                                            SecretoCifrado su, Instant ahora) {
        exigirVigente("actualizar su secreto");
        if ((tipoUsuario == TipoUsuario.GENERICO) != (su != null)) {
            throw new ReglaNegocioException("La contraseña su corresponde solo a un usuario Genérico");
        }
        int numero = versionVigente().map(CredencialVersion::getNumeroVersion).orElse(0) + 1;
        CredencialVersion version = CredencialVersion.nueva(this, numero, tipo, tipoUsuario, secreto, su, ahora);
        versiones.add(version);
        return version;
    }

    /** Las versiones se conservan: las ejecuciones registran con cuál se autenticaron */
    public void revocar(Instant ahora) {
        if (!estaVigente()) {
            throw new ReglaNegocioException("La credencial %s ya está revocada".formatted(nombre));
        }
        estado = EstadoCredencial.REVOCADA;
        fechaRevocacion = ahora;
    }

    public boolean estaVigente() {
        return estado == EstadoCredencial.VIGENTE;
    }

    public void exigirVigente(String accion) {
        if (!estaVigente()) {
            throw new ReglaNegocioException("La credencial %s está revocada: no se puede %s".formatted(nombre, accion));
        }
    }

    public Optional<CredencialVersion> versionVigente() {
        return versiones.stream().max(Comparator.comparingInt(CredencialVersion::getNumeroVersion));
    }

    private void aplicarDatos(String nombre, String usuarioAcceso, String descripcion) {
        this.nombre = nombre;
        this.usuarioAcceso = usuarioAcceso;
        this.descripcion = descripcion;
    }
}
