package com.komainos.seguridad.dominio;

import com.komainos.shared.dominio.Tiempo;
import com.komainos.shared.error.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

/**
 * Cuenta de usuario del sistema (RF03), tabla {@code usuario}.
 *
 * <p>Se usa {@code @Getter}/{@code @Setter} y no {@code @Data}: este ultimo
 * genera equals/hashCode sobre campos mutables y un toString que recorre
 * asociaciones lazy. En entidades JPA la identidad la da el id.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    /** Codigo con el que la persona inicia sesion (HU01 CA1). Unico. */
    @Column(name = "codigo", nullable = false, length = 100)
    private String codigo;

    @Column(name = "nombre_completo", nullable = false, length = 255)
    private String nombreCompleto;

    /** Solo el hash BCrypt; la contrasena en claro nunca se persiste. */
    @Column(name = "hash_contrasena", nullable = false, length = 255)
    private String hashContrasena;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "rol", nullable = false, columnDefinition = "enum_tipo_rol")
    private Rol rol;

    /**
     * La baja es logica: RF03 exige conservar el historial de las cuentas
     * desactivadas, y ordenes y auditorias antiguas las referencian.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public static Usuario nuevo(String codigo, String nombreCompleto, String hashContrasena, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.codigo = codigo;
        usuario.nombreCompleto = nombreCompleto;
        usuario.hashContrasena = hashContrasena;
        usuario.rol = rol;
        usuario.activo = true;
        return usuario;
    }

    public void activar() {
        if (activo) {
            throw new ReglaNegocioException("La cuenta %s ya se encuentra activa".formatted(codigo));
        }
        activo = true;
    }

    public void desactivar() {
        if (!activo) {
            throw new ReglaNegocioException("La cuenta %s ya se encuentra desactivada".formatted(codigo));
        }
        activo = false;
    }

    @PrePersist
    void alInsertar() {
        Instant ahora = Tiempo.ahora();
        fechaCreacion = ahora;
        fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = Tiempo.ahora();
    }
}
