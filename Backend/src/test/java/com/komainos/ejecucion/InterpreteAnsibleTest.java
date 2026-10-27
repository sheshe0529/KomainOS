package com.komainos.ejecucion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.InterpreteAnsible;
import com.komainos.ejecucion.service.SanitizadorSalida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La salida de ansible-playbook con el callback komainos_json. Los mensajes son los que Ansible y los servidores
 * devuelven en cada caso; RF71 exige distinguir comunicación, autenticación y permisos (CP022, CP023).
 */
@DisplayName("Interpretación de la salida de Ansible (RF52, RF71)")
class InterpreteAnsibleTest {

    private final ObjectMapper json = new ObjectMapper();

    private ResultadoEjecucion interpretar(String linea, PropositoEjecucion proposito) {
        String registro = "[WARNING]: aviso del controlador\n" + InterpreteAnsible.PREFIJO + linea + "\n";
        return InterpreteAnsible.interpretar(registro, 2, proposito, json, "clave-super-secreta");
    }

    @Test
    @DisplayName("Una tarea correcta conserva su salida")
    void correcto() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"ok\",\"rc\":0,\"stdout\":\"listo\",\"stderr\":\"\",\"msg\":\"\"}",
                PropositoEjecucion.TAREA);

        assertThat(r.correcto()).isTrue();
        assertThat(r.codigoRetorno()).isZero();
        assertThat(r.salida()).isEqualTo("listo");
    }

    @Test
    @DisplayName("Un código de retorno distinto de cero es un error de ejecución con su salida")
    void codigoDeError() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":2,\"stdout\":\"parcial\",\"stderr\":"
                + "\"No existe la carpeta\",\"msg\":\"non-zero return code\"}", PropositoEjecucion.TAREA);

        assertThat(r.fallo()).isEqualTo(TipoFallo.EJECUCION);
        assertThat(r.codigoRetorno()).isEqualTo(2);
        assertThat(r.salida()).isEqualTo("parcial");
        assertThat(r.error()).isEqualTo("Error de ejecución: No existe la carpeta");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Failed to connect to the host via ssh: ssh: connect to host 10.0.0.5 port 22: Connection timed out|COMUNICACION",
            "Failed to connect to the host via ssh: Host key verification failed.|COMUNICACION",
            "ssl: HTTPSConnectionPool(host='10.0.0.6', port=5986): Max retries exceeded|COMUNICACION",
            "Failed to connect to the host via ssh: deploy@10.0.0.5: Permission denied (publickey,password).|AUTENTICACION",
            "ntlm: the specified credentials were rejected by the server|AUTENTICACION",
            "Invalid/incorrect password: Permission denied, please try again.|AUTENTICACION"})
    @DisplayName("Un servidor inalcanzable distingue comunicación de autenticación")
    void inalcanzable(String mensaje, TipoFallo esperado) {
        ResultadoEjecucion r = interpretar("{\"estado\":\"unreachable\",\"rc\":null,\"stdout\":\"\",\"stderr\":\"\",\"msg\":"
                + json.valueToTree(mensaje) + "}", PropositoEjecucion.CONECTIVIDAD);

        assertThat(r.fallo()).isEqualTo(esperado);
        assertThat(r.codigoRetorno()).isNull();
    }

    @Test
    @DisplayName("La comprobación de acceso que termina con 3 es un problema de permisos (CP023)")
    void sinPermisos() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":3,\"stdout\":\"\",\"stderr\":"
                + "\"La cuenta deploy se autenticó, pero no es root\",\"msg\":\"\"}", PropositoEjecucion.CONECTIVIDAD);

        assertThat(r.fallo()).isEqualTo(TipoFallo.PERMISOS);
        assertThat(r.error()).contains("no es root");
    }

    @Test
    @DisplayName("Una escalada con su fallida es un problema de permisos aunque traiga código de retorno")
    void escaladaFallida() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":1,\"stdout\":\"\",\"stderr\":\"\",\"msg\":"
                + "\"Incorrect su password\"}", PropositoEjecucion.TAREA);

        assertThat(r.fallo()).isEqualTo(TipoFallo.PERMISOS);
    }

    @Test
    @DisplayName("El límite de tiempo de la tarea no se confunde con un problema de red")
    void tiempoExcedido() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":null,\"stdout\":\"\",\"stderr\":\"\",\"msg\":"
                + "\"The ansible.builtin.script action failed to execute in the expected time frame (3600) and was "
                + "terminated\"}", PropositoEjecucion.TAREA);

        assertThat(r.fallo()).isEqualTo(TipoFallo.TIEMPO_EXCEDIDO);
    }

    @Test
    @DisplayName("Sin línea de resultado se rescatan las líneas ERROR y se indica cómo resolverlo")
    void errorDeConfiguracion() {
        ResultadoEjecucion r = InterpreteAnsible.interpretar("""
                ERROR! couldn't resolve module/action 'ansible.windows.win_shell'. This often indicates a misspelling
                """, 4, PropositoEjecucion.TAREA, json);

        assertThat(r.fallo()).isEqualTo(TipoFallo.CONFIGURACION);
        assertThat(r.error()).contains("couldn't resolve module/action").contains("ansible-galaxy collection install");
    }

    @Test
    @DisplayName("RNF11: ni la salida ni el error conservan la contraseña de la cuenta")
    void sinSecretos() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":1,\"stdout\":\"usando clave-super-secreta\","
                + "\"stderr\":\"password=otra-clave token: abc\",\"msg\":\"\"}", PropositoEjecucion.TAREA);

        assertThat(r.salida()).doesNotContain("clave-super-secreta");
        assertThat(r.error()).doesNotContain("otra-clave", "abc");
    }

    @Test
    @DisplayName("RNF11: el saneamiento quita llaves privadas y asignaciones con aspecto de secreto")
    void sanitizador() {
        String limpio = SanitizadorSalida.limpiar("""
                password=abc
                "ansible_password": "def",
                -----BEGIN OPENSSH PRIVATE KEY-----
                privada
                -----END OPENSSH PRIVATE KEY-----
                clave: valor-secreto""", "valor-secreto");

        assertThat(limpio).doesNotContain("abc", "def", "privada", "valor-secreto");
    }
}
