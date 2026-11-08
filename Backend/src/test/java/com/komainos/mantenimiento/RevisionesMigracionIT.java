package com.komainos.mantenimiento;

import com.komainos.PruebaIntegracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RevisionesMigracionIT extends PruebaIntegracion {

    @Autowired DataSource dataSource;

    @Test
    @DisplayName("RF37: las migraciones conservan la revisión individual y habilitan el vínculo grupal")
    void conservaRevisionesIndividualesYVinculaLasNuevasGrupales() throws Exception {
        String esquema = "\"revisiones_it_" + UUID.randomUUID().toString().replace("-", "") + "\"";
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try (var sql = conexion.createStatement()) {
                sql.execute("CREATE SCHEMA " + esquema);
                sql.execute("SET LOCAL search_path TO " + esquema);
                sql.execute("""
                        CREATE TYPE enum_tipo_evaluacion AS ENUM ('REVISION_PERIODICA', 'PREVIA', 'POSTERIOR');
                        CREATE TABLE grupo_mantenimiento (id_grupo_mantenimiento integer PRIMARY KEY);
                        CREATE TABLE configuracion_servidor (id_servidor integer PRIMARY KEY);
                        CREATE TABLE configuracion_grupo (id_grupo_mantenimiento integer PRIMARY KEY);
                        CREATE TABLE evaluacion (id_evaluacion integer PRIMARY KEY, id_servidor integer NOT NULL,
                            tipo enum_tipo_evaluacion NOT NULL, fecha_inicio timestamp(0) NOT NULL);
                        INSERT INTO grupo_mantenimiento VALUES (7);
                        INSERT INTO configuracion_grupo VALUES (7);
                        INSERT INTO configuracion_servidor VALUES (3);
                        INSERT INTO evaluacion VALUES (1, 3, 'REVISION_PERIODICA', '2026-11-01 12:00:00');
                        """);

                aplicar(conexion, esquema, "2026-11-07_revision_periodica_habilitada.sql");
                aplicar(conexion, esquema, "2026-11-07_revisiones_periodicas_grupales.sql");

                try (var fila = sql.executeQuery("""
                        SELECT s.revision_periodica_habilitada, g.revision_periodica_habilitada
                        FROM configuracion_servidor s CROSS JOIN configuracion_grupo g
                        WHERE s.id_servidor = 3 AND g.id_grupo_mantenimiento = 7
                        """)) {
                    assertThat(fila.next()).isTrue();
                    assertThat(fila.getObject(1, Boolean.class)).isTrue();
                    assertThat(fila.getObject(2, Boolean.class)).isFalse();
                }
                sql.executeUpdate("""
                        WITH revision AS (
                            INSERT INTO revision_periodica_grupo (id_grupo_mantenimiento, fecha_inicio)
                            VALUES (7, '2026-11-07 12:00:00') RETURNING id_revision_periodica_grupo
                        )
                        INSERT INTO evaluacion (id_evaluacion, id_servidor, tipo, fecha_inicio, id_revision_periodica_grupo)
                        SELECT 2, 3, 'REVISION_PERIODICA'::enum_tipo_evaluacion, TIMESTAMP '2026-11-07 12:00:00',
                            id_revision_periodica_grupo FROM revision
                        """);
                try (var filas = sql.executeQuery("""
                        SELECT e.id_evaluacion, e.id_revision_periodica_grupo, r.id_grupo_mantenimiento
                        FROM evaluacion e LEFT JOIN revision_periodica_grupo r USING (id_revision_periodica_grupo)
                        ORDER BY e.id_evaluacion
                        """)) {
                    assertThat(filas.next()).isTrue();
                    assertThat(filas.getInt(1)).isEqualTo(1);
                    assertThat(filas.getObject(2)).isNull();
                    assertThat(filas.next()).isTrue();
                    assertThat(filas.getInt(1)).isEqualTo(2);
                    assertThat(filas.getObject(2)).isNotNull();
                    assertThat(filas.getInt(3)).isEqualTo(7);
                    assertThat(filas.next()).isFalse();
                }
            } finally {
                conexion.rollback();
            }
        }
    }

    private static void aplicar(Connection conexion, String esquema, String archivo) throws IOException {
        String cambio = Files.readString(Path.of("scripts/bd/cambios", archivo))
                .replace("\"KomainOS\"", esquema)
                .replaceAll("(?m)^BEGIN;\\s*$|^COMMIT;\\s*$", "");
        ScriptUtils.executeSqlScript(conexion, new ByteArrayResource(cambio.getBytes(StandardCharsets.UTF_8)));
    }
}
