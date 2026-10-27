package com.komainos.ejecucion.service;

import com.komainos.ejecucion.config.ModoAutomatizacion;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.shared.util.Tiempo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Estado del transporte para el panel: modo, controlador y si ansible-playbook responde. Así se sabe antes de habilitar
 * el modo REAL si el controlador está listo. No expone secretos ni rutas de archivos de credenciales. La comprobación de
 * Ansible se guarda unos minutos porque lanza un proceso.
 */
@Service
public class DiagnosticoAutomatizacion {

    private static final Duration VIGENCIA = Duration.ofMinutes(5);

    private final PropiedadesAutomatizacion propiedades;
    private final Clock reloj;
    private volatile Ansible ultimaComprobacion;

    public DiagnosticoAutomatizacion(PropiedadesAutomatizacion propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public Diagnostico consultar() {
        Ansible ansible = comprobarAnsible();
        PropiedadesAutomatizacion.Ansible config = propiedades.ansible();
        String sistema = System.getProperty("os.name", "desconocido");
        boolean controladorLinux = sistema.toLowerCase(Locale.ROOT).contains("linux");

        List<String> avisos = new ArrayList<>();
        if (propiedades.simulada()) {
            avisos.add("Modo simulado: los análisis y las tareas devuelven resultados de prueba y no se conecta a los "
                    + "servidores.");
        } else if (!ansible.disponible()) {
            avisos.add("Modo REAL sin Ansible disponible: las operaciones remotas fallarán hasta instalarlo.");
        }
        if (!config.ssh().verificarClaveHost()) {
            avisos.add("La verificación de la clave SSH de los servidores está desactivada: úsela solo en laboratorio.");
        }
        if (!config.winrm().validarCertificado()) {
            avisos.add("La validación del certificado WinRM está desactivada: úsela solo en laboratorio.");
        }
        if (!controladorLinux) {
            avisos.add("El controlador es %s: el modo REAL requiere Linux.".formatted(sistema));
        }
        if (!propiedades.procesoHabilitado()) {
            avisos.add("El proceso automático está desactivado: use «Procesar ciclo» para avanzar las órdenes.");
        }
        Transporte transporte = new Transporte(config.ssh().puerto(), config.ssh().verificarClaveHost(),
                config.winrm().puerto(), config.winrm().esquema(), config.winrm().transporte(),
                config.winrm().validarCertificado(), config.timeoutConexionSegundos());
        return new Diagnostico(propiedades.modo(), propiedades.simulada(), propiedades.procesoHabilitado(),
                propiedades.intervaloMs() / 1000, propiedades.maxConexionesSimultaneas(), sistema, controladorLinux,
                ansible.disponible(), ansible.version(), config.ejecutable(), transporte, avisos, ansible.verificado());
    }

    private Ansible comprobarAnsible() {
        Instant ahora = Tiempo.ahora(reloj);
        Ansible anterior = ultimaComprobacion;
        if (anterior != null && anterior.verificado().plus(VIGENCIA).isAfter(ahora)) {
            return anterior;
        }
        Ansible actual = new Ansible(false, null, ahora);
        try {
            Process proceso = new ProcessBuilder(propiedades.ansible().ejecutable(), "--version")
                    .redirectErrorStream(true).start();
            if (proceso.waitFor(15, TimeUnit.SECONDS) && proceso.exitValue() == 0) {
                String primera = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .lines().findFirst().orElse("").trim();
                actual = new Ansible(true, primera, ahora);
            } else {
                proceso.destroyForcibly();
            }
        } catch (IOException ex) {
            // No está instalado o no está en el PATH: es un estado válido, no un error
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        ultimaComprobacion = actual;
        return actual;
    }

    private record Ansible(boolean disponible, String version, Instant verificado) {
    }

    /** Configuración del transporte sin datos sensibles */
    public record Transporte(int puertoSsh, boolean verificarClaveHost, int puertoWinrm, String esquemaWinrm,
                             String transporteWinrm, boolean validarCertificadoWinrm, int timeoutConexionSegundos) {
    }

    public record Diagnostico(ModoAutomatizacion modo, boolean simulado, boolean procesoHabilitado,
                              long intervaloSegundos, int maxConexionesSimultaneas, String controlador,
                              boolean controladorCompatible, boolean ansibleDisponible, String versionAnsible,
                              String ejecutable, Transporte transporte, List<String> avisos, Instant verificado) {
    }
}
