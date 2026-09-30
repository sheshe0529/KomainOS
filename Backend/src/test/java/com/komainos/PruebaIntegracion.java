package com.komainos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Base de las pruebas de integracion (*IT): contexto completo contra el
 * PostgreSQL local, base {@code dbkomainos_test}, esquema {@code KomainOS}.
 *
 * <p>Que el contexto arranque ya prueba algo: el esquema se recreo con
 * scripts/bd/01_esquema.sql y 02_datos_sistema.sql, los mismos con los que se
 * crea una base nueva, y Hibernate valido que las entidades coinciden con las
 * tablas (DEC-22, DEC-34). Nunca apunta a DBKomainOS.
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
     * Vacia los datos de negocio conservando los de 02_datos_sistema.sql. Lo usan
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
                    ventana_mantenimiento, credencial_documental, direccion_ip, servidor, version_sistema_operativo,
                    sistema_operativo, nivel_criticidad, entorno, usuario
                RESTART IDENTITY""");
        jdbc.update("""
                UPDATE configuracion_sistema SET max_ejecuciones_concurrentes = 10, max_duracion_tarea_minutos = 60,
                    max_duracion_mop_minutos = 240, min_ciclos_racha_estable = 5, minutos_expiracion_token = 60""");
    }

    @TestConfiguration
    static class ConfiguracionPruebas {

        @Bean
        InicializadorBaseDePruebas inicializadorBaseDePruebas(DataSource dataSource) {
            return new InicializadorBaseDePruebas(dataSource);
        }

        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    /**
     * Recrea el esquema de pruebas con los scripts de scripts/bd, que son los
     * mismos que crean una base nueva (DEC-34). Spring Boot lo ejecuta antes de
     * iniciar JPA, de modo que Hibernate valida las entidades contra ese esquema.
     */
    static class InicializadorBaseDePruebas extends DataSourceScriptDatabaseInitializer {

        private final DataSource dataSource;

        InicializadorBaseDePruebas(DataSource dataSource) {
            super(dataSource, ajustes());
            this.dataSource = dataSource;
        }

        private static DatabaseInitializationSettings ajustes() {
            DatabaseInitializationSettings ajustes = new DatabaseInitializationSettings();
            ajustes.setSchemaLocations(List.of("file:scripts/bd/01_esquema.sql"));
            ajustes.setDataLocations(List.of("file:scripts/bd/02_datos_sistema.sql"));
            ajustes.setMode(DatabaseInitializationMode.ALWAYS);
            ajustes.setEncoding(StandardCharsets.UTF_8);
            return ajustes;
        }

        @Override
        public boolean initializeDatabase() {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            // Salvaguarda: se borra el esquema completo. Nunca sobre una base
            // que no sea explicitamente de pruebas.
            String base = jdbc.queryForObject("select current_database()", String.class);
            if (base == null || !base.toLowerCase().contains("test")) {
                throw new IllegalStateException(
                        "Las pruebas de integracion solo recrean bases de pruebas; se intento usar " + base);
            }
            jdbc.execute("DROP SCHEMA IF EXISTS \"KomainOS\" CASCADE");
            return super.initializeDatabase();
        }
    }
}
