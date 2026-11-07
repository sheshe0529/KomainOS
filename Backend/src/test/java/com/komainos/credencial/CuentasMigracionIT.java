package com.komainos.credencial;

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
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ejecuta el cambio SQL real sobre tablas de la versión anterior, en un esquema aislado y reversible. */
class CuentasMigracionIT extends PruebaIntegracion {
    @Autowired DataSource dataSource;

    @Test
    @DisplayName("RF05/RF06: la migración conserva cuentas propias y predeterminadas sin atribuirlas a órdenes históricas nulas")
    void conservaAsignacionesYDesconocidosHistoricos() throws Exception {
        String esquema = "cuentas_it_" + UUID.randomUUID().toString().replace("-", "");
        String prefijo = "\"" + esquema + "\".";
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                try (var sql = conexion.createStatement()) {
                    sql.execute("CREATE SCHEMA \"" + esquema + "\"");
                    sql.execute("CREATE TABLE " + prefijo + "cuenta_servicio (id_credencial integer PRIMARY KEY)");
                    sql.execute("CREATE TABLE " + prefijo + "configuracion_mantenimiento (id integer PRIMARY KEY, "
                            + "id_cuenta_servicio integer REFERENCES " + prefijo + "cuenta_servicio(id_credencial) ON DELETE RESTRICT)");
                    sql.execute("CREATE TABLE " + prefijo + "configuracion_sistema (id integer PRIMARY KEY, "
                            + "id_cuenta_servicio_predeterminada integer REFERENCES " + prefijo + "cuenta_servicio(id_credencial) ON DELETE RESTRICT)");
                    sql.execute("CREATE TABLE " + prefijo + "orden (id integer PRIMARY KEY, estado varchar(30) NOT NULL, "
                            + "id_cuenta_servicio integer REFERENCES " + prefijo + "cuenta_servicio(id_credencial) ON DELETE RESTRICT)");
                    sql.execute("INSERT INTO " + prefijo + "cuenta_servicio VALUES (7),(8),(9)");
                    sql.execute("INSERT INTO " + prefijo + "configuracion_mantenimiento VALUES (1,7),(2,NULL)");
                    sql.execute("INSERT INTO " + prefijo + "configuracion_sistema VALUES (1,8)");
                    sql.execute("INSERT INTO " + prefijo + "orden VALUES (10,'PROGRAMADA',7),(11,'CERRADA',NULL),"
                            + "(12,'PROGRAMADA',NULL),(13,'CERRADA',7)");
                }
                String cambio = Files.readString(Path.of("scripts/bd/cambios/2026-11-06_cuentas_por_finalidad.sql"))
                        .replace("\"KomainOS\"", "\"" + esquema + "\"")
                        .replaceAll("(?m)^BEGIN;\\s*$|^COMMIT;\\s*$", "");
                ScriptUtils.executeSqlScript(conexion, new ByteArrayResource(cambio.getBytes(StandardCharsets.UTF_8)));
                comprobarPar(conexion, prefijo + "configuracion_mantenimiento", "id = 1", 7, 7,
                        "id_cuenta_servicio_evaluacion", "id_cuenta_servicio_mantenimiento");
                comprobarPar(conexion, prefijo + "configuracion_mantenimiento", "id = 2", null, null,
                        "id_cuenta_servicio_evaluacion", "id_cuenta_servicio_mantenimiento");
                comprobarPar(conexion, prefijo + "configuracion_sistema", "id = 1", 8, 8,
                        "id_cuenta_servicio_evaluacion_predeterminada", "id_cuenta_servicio_mantenimiento_predeterminada");
                for (int id : new int[]{10, 13}) {
                    comprobarPar(conexion, prefijo + "orden", "id = " + id, 7, 7,
                            "id_cuenta_servicio_evaluacion", "id_cuenta_servicio_mantenimiento");
                }
                for (int id : new int[]{11, 12}) {
                    comprobarPar(conexion, prefijo + "orden", "id = " + id, null, null,
                            "id_cuenta_servicio_evaluacion", "id_cuenta_servicio_mantenimiento");
                }
                // Ambas columnas conservan referencia efectiva: ni una cuenta inexistente ni un borrado dejan huérfanos.
                try (var sql = conexion.createStatement()) {
                    sql.executeUpdate("UPDATE " + prefijo + "configuracion_mantenimiento SET id_cuenta_servicio_evaluacion = 9 WHERE id = 2");
                    var punto = conexion.setSavepoint();
                    assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM " + prefijo + "cuenta_servicio WHERE id_credencial = 9"))
                            .isInstanceOf(SQLException.class)
                            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isIn("23001", "23503"));
                    conexion.rollback(punto);
                    punto = conexion.setSavepoint();
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE " + prefijo + "orden SET id_cuenta_servicio_mantenimiento = 999 WHERE id = 10"))
                            .isInstanceOf(SQLException.class).satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23503"));
                    conexion.rollback(punto);
                }
            } finally {
                conexion.rollback();
            }
        }
    }

    private static void comprobarPar(Connection conexion, String tabla, String condicion, Integer evaluacion,
                                      Integer mantenimiento, String campoEvaluacion, String campoMantenimiento) throws SQLException {
        try (var sql = conexion.createStatement(); var fila = sql.executeQuery("SELECT " + campoEvaluacion + ", "
                + campoMantenimiento + " FROM " + tabla + " WHERE " + condicion)) {
            assertThat(fila.next()).isTrue();
            assertThat(fila.getObject(1, Integer.class)).isEqualTo(evaluacion);
            assertThat(fila.getObject(2, Integer.class)).isEqualTo(mantenimiento);
        }
    }
}
