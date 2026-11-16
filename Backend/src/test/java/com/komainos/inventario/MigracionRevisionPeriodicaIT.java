package com.komainos.inventario;

import com.komainos.PruebaIntegracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Migración de la revisión periódica a la configuración común")
class MigracionRevisionPeriodicaIT extends PruebaIntegracion {
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("Conserva true y false de ambas subclases, los identificadores y las frecuencias")
    void conservaValores() throws Exception {
        enEsquemaAislado((conexion, esquema) -> {
            conexion.createStatement().execute(migracion(esquema));
            try (var rs = conexion.createStatement().executeQuery("""
                    SELECT id_configuracion_mantenimiento, frecuencia_revision_dias, revision_periodica_habilitada
                    FROM configuracion_mantenimiento ORDER BY id_configuracion_mantenimiento
                    """)) {
                for (int id = 1; id <= 4; id++) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(id);
                    assertThat(rs.getInt(2)).isEqualTo(id * 7);
                    assertThat(rs.getBoolean(3)).isEqualTo(id % 2 == 1);
                }
                assertThat(rs.next()).isFalse();
            }
            try (var rs = conexion.createStatement().executeQuery("""
                    SELECT table_name, is_nullable FROM information_schema.columns
                    WHERE table_schema = '%s' AND column_name = 'revision_periodica_habilitada'
                    """.formatted(esquema))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("configuracion_mantenimiento");
                assertThat(rs.getString(2)).isEqualTo("NO");
                assertThat(rs.next()).isFalse();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO configuracion_mantenimiento VALUES (5, 35)",
            "INSERT INTO configuracion_grupo VALUES (1, false)"
    })
    @DisplayName("Una configuración huérfana o compartida aborta sin retirar los valores originales")
    void abortaDatosAmbiguos(String inconsistencia) throws Exception {
        enEsquemaAislado((conexion, esquema) -> {
            conexion.createStatement().execute(inconsistencia);
            String sql = migracion(esquema);
            assertThatThrownBy(() -> conexion.createStatement().execute(sql)).isInstanceOf(SQLException.class);
            conexion.createStatement().execute("ROLLBACK");
            try (var rs = conexion.createStatement().executeQuery("""
                    SELECT table_name FROM information_schema.columns
                    WHERE table_schema = '%s' AND column_name = 'revision_periodica_habilitada' ORDER BY table_name
                    """.formatted(esquema))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("configuracion_grupo");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("configuracion_servidor");
                assertThat(rs.next()).isFalse();
            }
        });
    }

    private String migracion(String esquema) throws Exception {
        return Files.readString(Path.of("scripts/bd/cambios/2026-11-15_centralizar_revision_periodica.sql"))
                .replace("SET LOCAL search_path TO \"KomainOS\"", "SET LOCAL search_path TO " + esquema);
    }

    private void enEsquemaAislado(Prueba prueba) throws Exception {
        String esquema = "prueba_revision_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conexion = dataSource.getConnection()) {
            String anterior = conexion.getSchema();
            try {
                conexion.createStatement().execute("CREATE SCHEMA " + esquema);
                conexion.setSchema(esquema);
                conexion.createStatement().execute("""
                        CREATE TABLE configuracion_mantenimiento (id_configuracion_mantenimiento integer PRIMARY KEY,
                            frecuencia_revision_dias integer NOT NULL);
                        CREATE TABLE configuracion_servidor (id_configuracion_mantenimiento integer PRIMARY KEY,
                            revision_periodica_habilitada boolean NOT NULL);
                        CREATE TABLE configuracion_grupo (id_configuracion_mantenimiento integer PRIMARY KEY,
                            revision_periodica_habilitada boolean NOT NULL);
                        INSERT INTO configuracion_mantenimiento VALUES (1, 7), (2, 14), (3, 21), (4, 28);
                        INSERT INTO configuracion_servidor VALUES (1, true), (2, false);
                        INSERT INTO configuracion_grupo VALUES (3, true), (4, false);
                        """);
                prueba.ejecutar(conexion, esquema);
            } finally {
                conexion.createStatement().execute("ROLLBACK");
                conexion.setSchema(anterior);
                conexion.createStatement().execute("DROP SCHEMA IF EXISTS " + esquema + " CASCADE");
            }
        }
    }

    private interface Prueba {
        void ejecutar(Connection conexion, String esquema) throws Exception;
    }
}
