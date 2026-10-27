package com.komainos.ejecucion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;
import java.util.Map;

/**
 * Configuración del componente de automatización (komainos.automatizacion). Ningún valor es secreto: las credenciales
 * de los servidores salen siempre de las cuentas de servicio cifradas (RNF02, RNF03).
 *
 * @param maxConexionesSimultaneas conexiones remotas abiertas a la vez entre análisis y tareas (RNF08)
 * @param escenarios               solo en SIMULADO: hostname y comportamiento que se reproduce para ese servidor
 */
@ConfigurationProperties("komainos.automatizacion")
public record PropiedadesAutomatizacion(@DefaultValue("SIMULADO") ModoAutomatizacion modo,
                                        @DefaultValue("false") boolean procesoHabilitado,
                                        @DefaultValue("30000") long intervaloMs,
                                        @DefaultValue("10") int maxConexionesSimultaneas,
                                        @DefaultValue Ansible ansible,
                                        @DefaultValue Simulacion simulacion,
                                        Map<String, String> escenarios) {

    public boolean simulada() {
        return modo == ModoAutomatizacion.SIMULADO;
    }

    /** El hostname se compara sin distinguir mayúsculas: el inventario los guarda en mayúsculas y YAML los escribe como se quiera */
    public EscenarioSimulado escenario(String hostname) {
        if (escenarios == null || hostname == null) {
            return EscenarioSimulado.ESTANDAR;
        }
        return escenarios.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(hostname))
                .map(e -> EscenarioSimulado.valueOf(e.getValue().trim().toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(EscenarioSimulado.ESTANDAR);
    }

    /**
     * @param ejecutable              ruta o nombre de ansible-playbook en el controlador
     * @param timeoutConexionSegundos espera máxima para abrir la conexión SSH o WinRM
     */
    public record Ansible(@DefaultValue("ansible-playbook") String ejecutable,
                          @DefaultValue("30") int timeoutConexionSegundos,
                          @DefaultValue Ssh ssh,
                          @DefaultValue Winrm winrm) {
    }

    /**
     * @param verificarClaveHost desactivarlo solo en laboratorio: sin verificación no se detecta un servidor suplantado
     * @param archivoKnownHosts  opcional; si falta se usa el known_hosts del usuario que ejecuta el backend
     */
    public record Ssh(@DefaultValue("22") int puerto,
                      @DefaultValue("true") boolean verificarClaveHost,
                      String archivoKnownHosts) {
    }

    /**
     * @param transporte          ntlm para cuentas locales, kerberos para cuentas de dominio
     * @param validarCertificado desactivarlo solo en laboratorio con certificados autofirmados
     */
    public record Winrm(@DefaultValue("5986") int puerto,
                        @DefaultValue("https") String esquema,
                        @DefaultValue("ntlm") String transporte,
                        @DefaultValue("true") boolean validarCertificado) {
    }

    /** @param retardoMs pausa por operación simulada, para observar los estados intermedios en el panel */
    public record Simulacion(@DefaultValue("0") long retardoMs) {
    }
}
