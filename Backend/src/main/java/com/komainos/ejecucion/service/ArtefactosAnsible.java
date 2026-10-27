package com.komainos.ejecucion.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion.Conexion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventario dinámico, playbook y entorno de cada invocación de ansible-playbook (R2.2, adaptador Ansible). Es código
 * puro, sin archivos ni procesos, para poder verificarlo en cualquier sistema operativo. El inventario contiene la
 * contraseña de la cuenta: el adaptador lo escribe con permisos 0600 y lo borra al terminar, y nunca pasa secretos como
 * argumentos del proceso, donde cualquier usuario del controlador los vería con ps.
 */
public final class ArtefactosAnsible {

    public static final String HOST = "objetivo";
    public static final String NOMBRE_TAREA = "komainos_resultado";
    public static final String CALLBACK = "komainos_json";

    private ArtefactosAnsible() {
    }

    /**
     * @param llavePrivada archivo con la llave cuando la cuenta autentica con llave SSH; nulo con contraseña
     */
    public static Map<String, Object> inventario(String direccionIp, FamiliaSistemaOperativo familia, Conexion conexion,
                                                 Path llavePrivada, PropiedadesAutomatizacion.Ansible config) {
        Map<String, Object> host = new LinkedHashMap<>();
        host.put("ansible_host", direccionIp);
        host.put("ansible_user", conexion.usuario());
        if (familia == FamiliaSistemaOperativo.WINDOWS) {
            PropiedadesAutomatizacion.Winrm winrm = config.winrm();
            host.put("ansible_connection", "winrm");
            host.put("ansible_port", winrm.puerto());
            host.put("ansible_winrm_scheme", winrm.esquema());
            host.put("ansible_winrm_transport", winrm.transporte());
            host.put("ansible_winrm_server_cert_validation", winrm.validarCertificado() ? "validate" : "ignore");
            host.put("ansible_password", conexion.secreto());
        } else {
            PropiedadesAutomatizacion.Ssh ssh = config.ssh();
            host.put("ansible_connection", "ssh");
            host.put("ansible_port", ssh.puerto());
            host.put("ansible_python_interpreter", "auto_silent");
            host.put("ansible_ssh_common_args", opcionesSsh(ssh));
            if (conexion.llave()) {
                host.put("ansible_ssh_private_key_file", llavePrivada.toString());
            } else {
                host.put("ansible_password", conexion.secreto());
            }
            if (conexion.secretoSu() != null) {
                host.put("ansible_become", true);
                host.put("ansible_become_method", "su");
                host.put("ansible_become_user", "root");
                host.put("ansible_become_password", conexion.secretoSu());
            }
        }
        return Map.of("all", Map.of("hosts", Map.of(HOST, host)));
    }

    /**
     * Una sola tarea con el módulo script, que copia el archivo, lo ejecuta y lo borra del servidor. El script ya trae
     * sus parámetros como variables: no hay argumentos que citar en la línea de comandos.
     */
    public static List<Map<String, Object>> playbook(Path script, FamiliaSistemaOperativo familia, int maxMinutos) {
        Map<String, Object> modulo = new LinkedHashMap<>();
        modulo.put("cmd", script.toString());
        if (familia != FamiliaSistemaOperativo.WINDOWS) {
            modulo.put("executable", "/bin/bash");
        }
        Map<String, Object> tarea = new LinkedHashMap<>();
        tarea.put("name", NOMBRE_TAREA);
        tarea.put("ansible.builtin.script", modulo);
        tarea.put("timeout", Math.max(1, maxMinutos) * 60);
        Map<String, Object> jugada = new LinkedHashMap<>();
        jugada.put("name", "KomainOS");
        jugada.put("hosts", HOST);
        jugada.put("gather_facts", false);
        jugada.put("tasks", List.of(tarea));
        return List.of(jugada);
    }

    /**
     * Configuración por variables de entorno, sin depender de un ansible.cfg del controlador: callback estructurado,
     * locale UTF-8 que exige Ansible, temporales dentro del directorio de la ejecución y sin conexiones SSH persistentes
     * que sobrevivan a la limpieza.
     */
    public static Map<String, String> entorno(Path directorio, PropiedadesAutomatizacion.Ansible config) {
        Map<String, String> entorno = new LinkedHashMap<>();
        entorno.put("ANSIBLE_STDOUT_CALLBACK", CALLBACK);
        entorno.put("ANSIBLE_CALLBACK_PLUGINS", directorio.toString());
        entorno.put("ANSIBLE_NOCOLOR", "1");
        entorno.put("ANSIBLE_FORCE_COLOR", "0");
        entorno.put("ANSIBLE_RETRY_FILES_ENABLED", "False");
        entorno.put("ANSIBLE_DEPRECATION_WARNINGS", "False");
        entorno.put("ANSIBLE_SYSTEM_WARNINGS", "False");
        entorno.put("ANSIBLE_HOST_KEY_CHECKING", config.ssh().verificarClaveHost() ? "True" : "False");
        entorno.put("ANSIBLE_TIMEOUT", String.valueOf(config.timeoutConexionSegundos()));
        entorno.put("ANSIBLE_LOCAL_TEMP", directorio.resolve("tmp").toString());
        entorno.put("ANSIBLE_SSH_ARGS", "-C -o ControlMaster=no");
        entorno.put("LANG", "C.UTF-8");
        entorno.put("LC_ALL", "C.UTF-8");
        return entorno;
    }

    private static String opcionesSsh(PropiedadesAutomatizacion.Ssh ssh) {
        if (!ssh.verificarClaveHost()) {
            return "-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null";
        }
        String opciones = "-o StrictHostKeyChecking=yes";
        if (ssh.archivoKnownHosts() != null && !ssh.archivoKnownHosts().isBlank()) {
            opciones += " -o UserKnownHostsFile=" + ssh.archivoKnownHosts().trim();
        }
        return opciones;
    }
}
