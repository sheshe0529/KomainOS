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

/** Contexto completo contra dbkomainos_test, nunca DBKomainOS, con el reloj fijo en AHORA para que la planificación sea reproducible */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaIntegracion.ConfiguracionPruebas.class)
public abstract class PruebaIntegracion {

    public static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    @Autowired
    protected JdbcTemplate jdbc;

    /** Vacía los datos de negocio y conserva los de 02_datos_sistema.sql, para pruebas con transacciones propias */
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
                    max_duracion_mop_minutos = 240, min_ciclos_racha_estable = 5, minutos_expiracion_token = 60,
                    id_cuenta_servicio_predeterminada = NULL""");
        // configuracion_sistema referencia cuenta_servicio: TRUNCATE no la admite, se borra en cascada desde credencial
        jdbc.update("DELETE FROM credencial");
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

    /** Recrea el esquema con scripts/bd antes de iniciar JPA, así Hibernate valida contra él (DEC-34) */
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
            // Salvaguarda: se borra el esquema completo, nunca sobre una base que no sea de pruebas
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
