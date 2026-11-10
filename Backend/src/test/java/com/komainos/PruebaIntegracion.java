package com.komainos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.service.DiagnosticoAutomatizacion;
import com.komainos.ejecucion.simulacion.AnsibleSimulado;
import com.komainos.ejecucion.simulacion.ValoresReferencia;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.mantenimiento.DatosDemostracionAutomatizacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.service.ServicioEvaluaciones;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Contexto completo contra dbkomainos_test, nunca DBKomainOS, con el reloj fijo en AHORA para que la planificación sea reproducible */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PruebaIntegracion.ConfiguracionPruebas.class, DatosDemostracionAutomatizacion.class})
public abstract class PruebaIntegracion {

    /**
     * Espía del doble del adaptador Ansible en el contexto común: un contexto distinto recrearía el esquema y conservaría
     * en JDBC los OID anteriores de los enumerados de PostgreSQL. Las pruebas lo usan para detener o fallar una operación.
     */
    @MockitoSpyBean
    protected AnsibleSimulado transporte;

    /**
     * Espía del diagnóstico del controlador real. La máquina de pruebas no tiene Ansible (y en Windows no es un
     * controlador compatible): las pruebas que dependen de un controlador listo lo declaran con doReturn.
     */
    @MockitoSpyBean
    protected DiagnosticoAutomatizacion diagnostico;

    public static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Vacía los datos de negocio y conserva los de 02_datos_sistema.sql, para pruebas con transacciones propias. Las
     * métricas del R3.1 también se vacían: cada prueba registra las que usa
     */
    protected void limpiarDatos() {
        jdbc.execute("""
                TRUNCATE TABLE alerta_destinatario, alerta, auditoria, incidencia, validacion_funcional,
                    validacion_tecnica, autorizacion_detalle, solicitud_autorizacion, notificacion_regla,
                    regla_politica_cambio, mop_tarea, mop, evaluacion_umbral, medicion_lista_item, medicion_metrica,
                    evaluacion, revision_periodica_grupo, cierre_orden, confirmacion_fuera_ventana, orden_detalle, historial_estado_orden,
                    orden, umbral_tarea, tarea, script_parametro, script_version, script_implementacion, script,
                    umbral_excepcion, umbral, lista_referencia_item, lista_referencia, metrica,
                    configuracion_grupo, configuracion_servidor,
                    configuracion_mantenimiento, grupo_servidor, grupo_mantenimiento, solicitud_baja,
                    ventana_mantenimiento, credencial_documental, direccion_ip, servidor, version_sistema_operativo,
                    sistema_operativo, nivel_criticidad, entorno, usuario
                RESTART IDENTITY""");
        jdbc.update("""
                UPDATE configuracion_sistema SET max_ejecuciones_concurrentes = 10, max_duracion_tarea_minutos = 60,
                    max_duracion_mop_minutos = 240, min_ciclos_racha_estable = 5, minutos_expiracion_token = 60,
                    id_cuenta_servicio_evaluacion_predeterminada = NULL, id_cuenta_servicio_mantenimiento_predeterminada = NULL""");
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

        /**
         * Reemplaza al adaptador real: ninguna prueba se conecta a un servidor. Los escenarios por hostname salen de
         * komainos.pruebas.escenarios (application-test.yml) y los valores de cada análisis, de los umbrales que rigen
         * para el servidor en el contexto de su evaluación (orden o revisión de grupo incluidas).
         */
        @Bean
        @Primary
        AnsibleSimulado transporteSimulado(ObjectMapper json, Environment entorno, PlatformTransactionManager transacciones,
                                           EvaluacionRepositorio evaluaciones, ServidorRepositorio servidores,
                                           MetricaRepositorio metricas, UmbralRepositorio umbrales) {
            Map<String, String> escenarios = Binder.get(entorno)
                    .bind("komainos.pruebas.escenarios", Bindable.mapOf(String.class, String.class))
                    .orElse(Map.of());
            TransactionTemplate lectura = new TransactionTemplate(transacciones);
            lectura.setReadOnly(true);
            return new AnsibleSimulado(json, escenarios, solicitud -> lectura.execute(estado -> {
                ContextoEvaluacion contexto = contexto(solicitud, evaluaciones, servidores);
                return ValoresReferencia.calcular(metricas.findAllByOrderByNombreAsc(), umbrales.findAplicables(), contexto);
            }));
        }

        private static ContextoEvaluacion contexto(SolicitudEjecucion solicitud, EvaluacionRepositorio evaluaciones,
                                                   ServidorRepositorio servidores) {
            if (solicitud.idEvaluacion() != null) {
                return ServicioEvaluaciones.contexto(evaluaciones.findById(solicitud.idEvaluacion()).orElseThrow());
            }
            return ContextoEvaluacion.individual(servidores.findById(solicitud.idServidor()).orElseThrow());
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
