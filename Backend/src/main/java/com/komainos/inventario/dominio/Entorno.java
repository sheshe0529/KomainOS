package com.komainos.inventario.dominio;

import com.komainos.shared.error.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entorno que clasifica los servidores (RF74), tabla {@code entorno}.
 *
 * <p>Se desactiva, no se borra: las ordenes historicas y la politica de
 * gestion del cambio lo referencian.
 */
@Entity
@Table(name = "entorno")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_entorno")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    public static Entorno nuevo(String nombre, String descripcion) {
        Entorno entorno = new Entorno();
        entorno.nombre = nombre;
        entorno.descripcion = descripcion;
        entorno.activo = true;
        return entorno;
    }

    public void activar() {
        if (activo) {
            throw new ReglaNegocioException("El entorno %s ya se encuentra activo".formatted(nombre));
        }
        activo = true;
    }

    public void desactivar() {
        if (!activo) {
            throw new ReglaNegocioException("El entorno %s ya se encuentra inactivo".formatted(nombre));
        }
        activo = false;
    }
}
