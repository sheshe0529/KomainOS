package com.komainos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Base de las pruebas de integracion (*IT): contexto completo contra el
 * PostgreSQL local, base {@code dbkomainos_test}, esquema {@code KomainOS}.
 *
 * <p>Que el contexto arranque ya prueba algo: Flyway aplico V1 y V2 sobre un
 * esquema vacio y Hibernate valido que las entidades coinciden con las tablas.
 * El esquema se limpia al crear el contexto (DEC-22); nunca apunta a DBKomainOS.
 *
 * <p>El reloj de los servicios queda fijo en {@link #AHORA} (lunes 2026-09-28
 * 09:00 en Lima) para que la planificacion sea reproducible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaIntegracion.ConfiguracionPruebas.class)
public abstract class PruebaIntegracion {

    public static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Vacia los datos de negocio conservando los datos iniciales de V2. Lo usan
     * las pruebas que no pueden envolverse en una transaccion porque ejercitan
     * transacciones propias (procesos automaticos, eventos tras confirmar).
     */
    protected void limpiarDatos() {
        jdbc.execute("""
                TRUNCATE TABLE alerta_destinatario, alerta, auditoria, incidencia, validacion_funcional,
                    validacion_tecnica, autorizacion_detalle, solicitud_autorizacion, notificacion_regla,
                    regla_politica_cambio, mop_tarea, mop, evaluacion_umbral, medicion_metrica, evaluacion,
                    cierre_orden, confirmacion_fuera_ventana, orden_detalle, historial_estado_orden,
                    programacion_orden, orden, umbral_tarea, tarea, script_parametro, script_version,
                    script_implementacion, script, umbral, metrica, configuracion_grupo, configuracion_servidor,
                    configuracion_mantenimiento, grupo_servidor, grupo_mantenimiento, solicitud_baja,
                    ventana_mantenimiento, credencial_documental, servidor, version_sistema_operativo,
                    sistema_operativo, nivel_criticidad, entorno, usuario
                RESTART IDENTITY""");
        jdbc.update("""
                UPDATE configuracion_sistema SET max_ejecuciones_concurrentes = 10, max_duracion_tarea_minutos = 60,
                    max_duracion_mop_minutos = 240, min_ciclos_racha_estable = 5, minutos_expiracion_token = 60""");
    }

    @TestConfiguration
    static class ConfiguracionPruebas {

        @Bean
        FlywayMigrationStrategy limpiarYMigrar() {
            return flyway -> {
                // Salvaguarda: clean() borra todo el esquema. Nunca sobre una base
                // que no sea explicitamente de pruebas.
                try (var conexion = flyway.getConfiguration().getDataSource().getConnection()) {
                    String base = conexion.getCatalog();
                    if (base == null || !base.toLowerCase().contains("test")) {
                        throw new IllegalStateException(
                                "Las pruebas de integracion solo limpian bases de pruebas; se intento usar " + base);
                    }
                } catch (java.sql.SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                flyway.clean();
                flyway.migrate();
            };
        }

        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }
}
