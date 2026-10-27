package com.komainos.ejecucion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion.Conexion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que recibe ansible-playbook en el modo REAL. No se ejecuta Ansible: se comprueba el inventario, el playbook y el
 * entorno que el adaptador escribe, que es donde se decide cómo se conecta y qué se expone.
 */
@DisplayName("Artefactos de Ansible del modo REAL (RF52, RNF09, RNF11)")
class ArtefactosAnsibleTest {

    private static final PropiedadesAutomatizacion.Ansible SEGURO = new PropiedadesAutomatizacion.Ansible(
            "ansible-playbook", 30,
            new PropiedadesAutomatizacion.Ssh(22, true, "/etc/komainos/known_hosts"),
            new PropiedadesAutomatizacion.Winrm(5986, "https", "ntlm", true));
    private static final PropiedadesAutomatizacion.Ansible LABORATORIO = new PropiedadesAutomatizacion.Ansible(
            "ansible-playbook", 15,
            new PropiedadesAutomatizacion.Ssh(2222, false, null),
            new PropiedadesAutomatizacion.Winrm(5985, "http", "ntlm", false));

    @SuppressWarnings("unchecked")
    private static Map<String, Object> host(Map<String, Object> inventario) {
        Map<String, Object> todos = (Map<String, Object>) inventario.get("all");
        return (Map<String, Object>) ((Map<String, Object>) todos.get("hosts")).get(ArtefactosAnsible.HOST);
    }

    @Test
    @DisplayName("Linux con contraseña y su: SSH con verificación de la clave del host y escalada a root")
    void linuxConSu() {
        Map<String, Object> h = host(ArtefactosAnsible.inventario("10.20.0.5", FamiliaSistemaOperativo.LINUX,
                new Conexion("deploy", "clave", "clave-root", false), null, SEGURO));

        assertThat(h).containsEntry("ansible_host", "10.20.0.5")
                .containsEntry("ansible_user", "deploy")
                .containsEntry("ansible_connection", "ssh")
                .containsEntry("ansible_port", 22)
                .containsEntry("ansible_password", "clave")
                .containsEntry("ansible_become", true)
                .containsEntry("ansible_become_method", "su")
                .containsEntry("ansible_become_password", "clave-root")
                .containsEntry("ansible_ssh_common_args",
                        "-o StrictHostKeyChecking=yes -o UserKnownHostsFile=/etc/komainos/known_hosts");
    }

    @Test
    @DisplayName("Linux con llave: la llave va como archivo y nunca como texto en el inventario")
    void linuxConLlave() {
        Map<String, Object> h = host(ArtefactosAnsible.inventario("10.20.0.5", FamiliaSistemaOperativo.LINUX,
                new Conexion("deploy", "-----BEGIN KEY-----", null, true), Path.of("/tmp/k/llave_privada"), LABORATORIO));

        assertThat(h).containsEntry("ansible_ssh_private_key_file", Path.of("/tmp/k/llave_privada").toString())
                .doesNotContainKey("ansible_password")
                .doesNotContainKey("ansible_become")
                .containsEntry("ansible_port", 2222)
                .containsEntry("ansible_ssh_common_args", "-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null");
        assertThat(h.values()).noneMatch(v -> String.valueOf(v).contains("BEGIN KEY"));
    }

    @Test
    @DisplayName("Windows: WinRM con certificado validado o, solo en laboratorio, ignorado")
    void windows() {
        Map<String, Object> seguro = host(ArtefactosAnsible.inventario("10.20.0.9", FamiliaSistemaOperativo.WINDOWS,
                new Conexion("svc_komainos", "clave", null, false), null, SEGURO));
        Map<String, Object> laboratorio = host(ArtefactosAnsible.inventario("10.20.0.9",
                FamiliaSistemaOperativo.WINDOWS, new Conexion("svc_komainos", "clave", null, false), null, LABORATORIO));

        assertThat(seguro).containsEntry("ansible_connection", "winrm")
                .containsEntry("ansible_port", 5986)
                .containsEntry("ansible_winrm_scheme", "https")
                .containsEntry("ansible_winrm_transport", "ntlm")
                .containsEntry("ansible_winrm_server_cert_validation", "validate");
        assertThat(laboratorio).containsEntry("ansible_winrm_server_cert_validation", "ignore")
                .containsEntry("ansible_port", 5985);
    }

    @Test
    @DisplayName("El playbook ejecuta el archivo sin argumentos: los parámetros ya van dentro del script")
    @SuppressWarnings("unchecked")
    void playbook() throws Exception {
        List<Map<String, Object>> linux = ArtefactosAnsible.playbook(Path.of("/tmp/k/tarea.sh"), FamiliaSistemaOperativo.LINUX, 60);
        List<Map<String, Object>> windows = ArtefactosAnsible.playbook(Path.of("/tmp/k/tarea.ps1"), FamiliaSistemaOperativo.WINDOWS, 5);

        Map<String, Object> tareaLinux = ((List<Map<String, Object>>) linux.getFirst().get("tasks")).getFirst();
        Map<String, Object> tareaWindows = ((List<Map<String, Object>>) windows.getFirst().get("tasks")).getFirst();
        assertThat(linux.getFirst()).containsEntry("hosts", ArtefactosAnsible.HOST).containsEntry("gather_facts", false);
        assertThat(tareaLinux).containsEntry("name", ArtefactosAnsible.NOMBRE_TAREA).containsEntry("timeout", 3600);
        assertThat((Map<String, Object>) tareaLinux.get("ansible.builtin.script"))
                .containsEntry("cmd", Path.of("/tmp/k/tarea.sh").toString())
                .containsEntry("executable", "/bin/bash");
        assertThat((Map<String, Object>) tareaWindows.get("ansible.builtin.script"))
                .containsEntry("cmd", Path.of("/tmp/k/tarea.ps1").toString())
                .doesNotContainKey("executable");
        // JSON es YAML válido: es el formato con que se escribe el playbook
        assertThat(new ObjectMapper().writeValueAsString(linux)).contains("\"ansible.builtin.script\"");
    }

    @Test
    @DisplayName("El entorno fija el callback estructurado, UTF-8 y temporales dentro del directorio de la ejecución")
    void entorno() {
        Map<String, String> entorno = ArtefactosAnsible.entorno(Path.of("/tmp/k"), SEGURO);

        assertThat(entorno).containsEntry("ANSIBLE_STDOUT_CALLBACK", "komainos_json")
                .containsEntry("ANSIBLE_CALLBACK_PLUGINS", Path.of("/tmp/k").toString())
                .containsEntry("ANSIBLE_HOST_KEY_CHECKING", "True")
                .containsEntry("ANSIBLE_TIMEOUT", "30")
                .containsEntry("ANSIBLE_LOCAL_TEMP", Path.of("/tmp/k/tmp").toString())
                .containsEntry("LC_ALL", "C.UTF-8");
        assertThat(ArtefactosAnsible.entorno(Path.of("/tmp/k"), LABORATORIO))
                .containsEntry("ANSIBLE_HOST_KEY_CHECKING", "False");
    }

    @Test
    @DisplayName("La llave privada queda sin retornos de carro y con salto de línea final, como exige ssh")
    void llaveNormalizada() {
        assertThat(AnsibleReal.normalizarLlave("-----BEGIN KEY-----\r\nabc\r\n-----END KEY-----"))
                .isEqualTo("-----BEGIN KEY-----\nabc\n-----END KEY-----\n");
    }
}
