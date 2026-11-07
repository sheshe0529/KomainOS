package com.komainos.catalogo;

import com.komainos.PruebaIntegracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba el cambio real de PostgreSQL sobre un esquema aislado que se deshace siempre al terminar. */
class ParametrosMigracionIT extends PruebaIntegracion {
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("RF25/RF26: la migración elimina obligatorio sin reescribir valores ni versiones históricas")
    void conservaVersionesYValoresHistoricos() throws Exception {
        String esquema = "parametros_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                String prefijo = "\"" + esquema + "\".";
                try (var sql = conexion.createStatement()) {
                    sql.execute("CREATE SCHEMA \"" + esquema + "\"");
                    sql.execute("CREATE TYPE " + prefijo + "enum_tipo_dato_parametro AS ENUM ('STRING','INTEGER','DECIMAL','BOOLEAN')");
                    sql.execute("CREATE TABLE " + prefijo + "lista_referencia (id_lista_referencia integer PRIMARY KEY)");
                    sql.execute("CREATE TABLE " + prefijo + "script_version (id_script_version integer PRIMARY KEY)");
                    sql.execute("CREATE TABLE " + prefijo + "script_parametro (id_script_parametro integer PRIMARY KEY, "
                            + "tipo_dato " + prefijo + "enum_tipo_dato_parametro NOT NULL, obligatorio boolean NOT NULL DEFAULT false, "
                            + "valor_por_defecto varchar(1000), orden integer NOT NULL)");
                    sql.execute("INSERT INTO " + prefijo + "script_version VALUES (1)");
                    sql.execute("INSERT INTO " + prefijo + "script_parametro VALUES (1, 'STRING', false, NULL, 1),"
                            + "(2, 'INTEGER', true, '7', 2)");
                }
                String cambio = Files.readString(Path.of("scripts/bd/cambios/2026-11-06_parametros_listas.sql"))
                        .replace("\"KomainOS\"", "\"" + esquema + "\"")
                        .replaceAll("(?m)^BEGIN;\\s*$|^COMMIT;\\s*$", "");
                ScriptUtils.executeSqlScript(conexion, new ByteArrayResource(cambio.getBytes(StandardCharsets.UTF_8)));
                try (var sql = conexion.createStatement()) {
                    try (var fila = sql.executeQuery("SELECT valor_por_defecto, orden FROM " + prefijo
                            + "script_parametro WHERE id_script_parametro = 1")) {
                        assertThat(fila.next()).isTrue();
                        assertThat(fila.getString(1)).isNull();
                        assertThat(fila.getInt(2)).isEqualTo(1);
                    }
                    try (var fila = sql.executeQuery("SELECT listas_explicitas FROM " + prefijo + "script_version WHERE id_script_version = 1")) {
                        assertThat(fila.next()).isTrue();
                        assertThat(fila.getBoolean(1)).isFalse();
                    }
                    try (var fila = sql.executeQuery("SELECT valor_por_defecto FROM " + prefijo
                            + "script_parametro WHERE id_script_parametro = 2")) {
                        assertThat(fila.next()).isTrue();
                        assertThat(fila.getString(1)).isEqualTo("7");
                    }
                    sql.execute("INSERT INTO " + prefijo + "script_version (id_script_version) VALUES (2)");
                    try (var fila = sql.executeQuery("SELECT listas_explicitas FROM " + prefijo + "script_version WHERE id_script_version = 2")) {
                        assertThat(fila.next()).isTrue();
                        assertThat(fila.getBoolean(1)).isTrue();
                    }
                    try (var fila = sql.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema = '"
                            + esquema + "' AND table_name = 'script_parametro' AND column_name = 'obligatorio'")) {
                        assertThat(fila.next()).isTrue();
                        assertThat(fila.getInt(1)).isZero();
                    }
                }
            } finally {
                conexion.rollback();
            }
        }
    }
}
