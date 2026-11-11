package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.service.ServicioConectividad.Permisos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prueba de acceso: los privilegios efectivos se informan sin exigirse (RF71, DEC-57)")
class PermisosAccesoTest {

    @Test
    @DisplayName("La marca final dice si los scripts tendrán privilegios de administrador y no llega al mensaje")
    void leeLaMarca() {
        Permisos root = ServicioConectividad.permisos("""
                Acceso verificado en srv01: los scripts se ejecutarán como root (uid 0), con privilegios de administrador
                KOMAINOS_ADMINISTRADOR=si
                """);
        Permisos usuario = ServicioConectividad.permisos(
                "\nAcceso verificado en srv01: los scripts se ejecutarán como ops (uid 1001), sin privilegios\n"
                        + "KOMAINOS_ADMINISTRADOR=no\n");

        assertThat(root.administrador()).isTrue();
        assertThat(root.mensaje()).startsWith("Acceso verificado").doesNotContain("KOMAINOS_ADMINISTRADOR");
        assertThat(usuario.administrador()).isFalse();
        assertThat(usuario.mensaje()).isEqualTo(
                "Acceso verificado en srv01: los scripts se ejecutarán como ops (uid 1001), sin privilegios");
    }

    @Test
    @DisplayName("Sin la marca no se afirma nada sobre los privilegios")
    void sinMarca() {
        Permisos p = ServicioConectividad.permisos("Acceso verificado como deploy en srv01");

        assertThat(p.administrador()).isNull();
        assertThat(p.mensaje()).isEqualTo("Acceso verificado como deploy en srv01");
    }
}
