package com.komainos.ejecucion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.ContratoAnalisis;
import com.komainos.ejecucion.service.InterpreteAnsible;
import com.komainos.ejecucion.service.SanitizadorSalida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

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
    @DisplayName("DEC-57: sudo con contraseña incorrecta es un problema de permisos con su indicación")
    void sudoContrasenaIncorrecta() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":null,\"stdout\":\"\",\"stderr\":\"\",\"msg\":"
                + "\"Task failed: Incorrect sudo password\"}", PropositoEjecucion.CONECTIVIDAD);

        assertThat(r.fallo()).isEqualTo(TipoFallo.PERMISOS);
        assertThat(r.error()).contains("propio usuario");
    }

    @Test
    @DisplayName("DEC-57: sudo que pide una contraseña no registrada llega por la salida estándar y es un problema de permisos")
    void sudoPideContrasena() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":1,\"stdout\":\"sudo: interactive "
                + "authentication is required\\r\\n\",\"stderr\":\"Shared connection to 10.0.0.5 closed.\",\"msg\":"
                + "\"non-zero return code\"}", PropositoEjecucion.TAREA);

        assertThat(r.fallo()).isEqualTo(TipoFallo.PERMISOS);
        assertThat(r.error()).contains("interactive authentication is required").contains("NOPASSWD");
    }

    @Test
    @DisplayName("DEC-57: un «access denied» que imprime el script no se toma por una elevación fallida")
    void accesoDenegadoDelScript() {
        ResultadoEjecucion r = interpretar("{\"estado\":\"failed\",\"rc\":2,\"stdout\":\"access denied to /data\","
                + "\"stderr\":\"\",\"msg\":\"non-zero return code\"}", PropositoEjecucion.TAREA);

        assertThat(r.fallo()).isEqualTo(TipoFallo.EJECUCION);
        assertThat(r.codigoRetorno()).isEqualTo(2);
    }

    @Test
    @DisplayName("DEC-57: la salida queda sin los restos de la terminal con la que Ansible eleva privilegios")
    void restosDeTerminal() {
        String salida = "***\b \b\b \b\b \b\r\nse ejecuta como root (uid 0)\r\n\u001B]3008;end=a88dda0c\u001B\\";
        ResultadoEjecucion r = interpretar("{\"estado\":\"ok\",\"rc\":0,\"stdout\":" + json.valueToTree(salida)
                + ",\"stderr\":\"\",\"msg\":\"\"}", PropositoEjecucion.TAREA);

        assertThat(r.salida()).isEqualTo("\nse ejecuta como root (uid 0)\n");
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

    @Test
    @DisplayName("Los estados de bloqueo de root y los permisos de archivos llegan completos desde una tarea")
    void conservaIndicadoresDeMantenimiento() throws Exception {
        String salida = """
                [2026-11-18 17:59:15] OBSERVADO=root_contrasena:no_bloqueada
                [2026-11-18 17:59:15] CAMBIO=root_contrasena:bloqueada
                [2026-11-18 17:59:15] OBSERVADO=/etc/passwd:644
                [2026-11-18 17:59:15] ESTADO=APLICADO
                """;

        ResultadoEjecucion resultado = interpretar(json.writeValueAsString(
                Map.of("estado", "ok", "rc", 0, "stdout", salida)), PropositoEjecucion.TAREA);

        assertThat(resultado.correcto()).isTrue();
        assertThat(resultado.salida()).isEqualTo(salida);
    }

    @Test
    @DisplayName("El saneamiento conserva el JSON y los permisos que necesita el contrato del análisis")
    void conservaPermisosEnAnalisis() throws Exception {
        List<String> permisos = List.of("/etc/passwd:644", "/etc/group:644");
        String salida = json.writeValueAsString(Map.of("metricas", List.of(
                Map.of("codigo", "arch_crit_permisos", "elementos", permisos))));
        ResultadoEjecucion interpretado = interpretar(json.writeValueAsString(
                Map.of("estado", "ok", "rc", 0, "stdout", salida)), PropositoEjecucion.ANALISIS_PREVIO);

        ResultadoEjecucion resultado = ContratoAnalisis.aplicar(interpretado, json);

        assertThat(resultado.correcto()).isTrue();
        assertThat(resultado.metricas()).hasSize(1);
        assertThat(resultado.metricas().getFirst().elementos()).containsExactlyElementsOf(permisos);
    }

    @Test
    @DisplayName("Ocultar secretos JSON conserva comillas, caracteres escapados y valores contiguos")
    void secretosSinRomperJson() throws Exception {
        String salida = json.writeValueAsString(Map.of(
                "password", "una clave con espacios y \"comillas\"",
                "datos", List.of(Map.of("token", 12345, "archivo", "/etc/passwd:644")),
                "estado", "APLICADO"));

        String limpio = SanitizadorSalida.limpiar(salida);

        var protegido = json.readTree(limpio);
        assertThat(protegido.path("password").asText()).isEqualTo("[PROTEGIDO]");
        assertThat(protegido.path("datos").get(0).path("token").asText()).isEqualTo("[PROTEGIDO]");
        assertThat(protegido.path("datos").get(0).path("archivo").asText()).isEqualTo("/etc/passwd:644");
        assertThat(protegido.path("estado").asText()).isEqualTo("APLICADO");
        assertThat(limpio).doesNotContain("12345", "una clave", "comillas");
    }

    @Test
    @DisplayName("Las asignaciones sensibles completas y los secretos conocidos continúan protegidos")
    void conservaProteccionDeSecretos() {
        String salida = "PASSWORD=abc passwd: def contraseña='clave con espacios' "
                + "ansible_become_pass=ghi OBSERVADO=root_contrasena:clave-super-secreta";

        String limpio = SanitizadorSalida.limpiar(salida, "clave-super-secreta");

        assertThat(limpio).isEqualTo("PASSWORD=[PROTEGIDO] passwd: [PROTEGIDO] "
                + "contraseña='[PROTEGIDO]' ansible_become_pass=[PROTEGIDO] "
                + "OBSERVADO=root_contrasena:[PROTEGIDO]");
    }

    @Test
    @DisplayName("Una asignación con el secreto conocido conserva un único marcador al sanearse de nuevo")
    void marcadorProtegidoEstable() {
        String limpio = SanitizadorSalida.limpiar("password=clave-super-secreta", "clave-super-secreta");

        assertThat(limpio).isEqualTo("password=[PROTEGIDO]");
        assertThat(SanitizadorSalida.limpiar(limpio)).isEqualTo(limpio);
    }

    @Test
    @DisplayName("Una contraseña entre comillas del tamaño máximo se protege completa")
    void secretoEntreComillasLargo() {
        String salida = "{\"password\":\"" + "x".repeat(SanitizadorSalida.LARGO_MAXIMO) + "\"}";

        assertThat(SanitizadorSalida.limpiar(salida)).isEqualTo("{\"password\":\"[PROTEGIDO]\"}");
    }

    @ParameterizedTest
    @DisplayName("RNF11: las claves compuestas que terminan en un nombre sensible siguen protegidas")
    @CsvSource(delimiter = '|', textBlock = """
            DB_PASSWORD=hunter2                     | DB_PASSWORD=[PROTEGIDO]
            export MYSQL_ROOT_PASSWORD=hunter2      | export MYSQL_ROOT_PASSWORD=[PROTEGIDO]
            spring.datasource.password: hunter2     | spring.datasource.password: [PROTEGIDO]
            mysql --password=hunter2 -u root        | mysql --password=[PROTEGIDO] -u root
            X-Auth-Token: abc123                    | X-Auth-Token: [PROTEGIDO]
            GET /api?token=abc123                   | GET /api?token=[PROTEGIDO]
            + $password = Secreto123                | + $password = [PROTEGIDO]
            """)
    void protegeClavesCompuestas(String salida, String esperado) {
        assertThat(SanitizadorSalida.limpiar(salida)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @DisplayName("Las rutas, los valores de otra asignación y los nombres que solo contienen la palabra no se ocultan")
    @CsvSource(delimiter = '|', textBlock = """
            OBSERVADO=/etc/security/opasswd:600
            OBSERVADO=/etc/passwd=644
            CAMBIO=root_contrasena:bloqueada
            HKLM LocalAccountTokenFilterPolicy=1
            Contraseña de root: bloqueada
            PasswordComplexity = 1
            """)
    void conservaFalsosPositivos(String salida) {
        assertThat(SanitizadorSalida.limpiar(salida)).isEqualTo(salida);
    }

    @Test
    @DisplayName("En JSON se ocultan las claves sensibles y se conservan los elementos de lista que solo las mencionan")
    void jsonConClavesYElementos() throws Exception {
        String salida = json.writeValueAsString(Map.of(
                "accessToken", "abc123",
                "elementos", List.of("root_contrasena:bloqueada", "/etc/passwd:644")));

        var limpio = json.readTree(SanitizadorSalida.limpiar(salida));

        assertThat(limpio.path("accessToken").asText()).isEqualTo("[PROTEGIDO]");
        assertThat(limpio.path("elementos").get(0).asText()).isEqualTo("root_contrasena:bloqueada");
        assertThat(limpio.path("elementos").get(1).asText()).isEqualTo("/etc/passwd:644");
    }

    private ResultadoEjecucion fallido(int codigo, String stdout, String stderr) throws Exception {
        return interpretar(json.writeValueAsString(Map.of("estado", "failed", "rc", codigo, "stdout", stdout,
                "stderr", stderr, "msg", "non-zero return code")), PropositoEjecucion.ANALISIS_PREVIO);
    }

    @Test
    @DisplayName("Con elevación en una terminal, el error de Bash de la salida explica el fallo y no el cierre de la sesión SSH")
    void errorDeSintaxisEnTerminal() throws Exception {
        ResultadoEjecucion r = fallido(2,
                "/home/komainos-gen/.ansible/tmp/ansible-tmp-1731-99-1/tarea.sh: line 4: unexpected EOF while looking "
                        + "for matching `''\r\n",
                "Connection to 172.31.37.104 closed.\r\n");

        assertThat(r.fallo()).isEqualTo(TipoFallo.EJECUCION);
        assertThat(r.error()).isEqualTo(
                "Error de ejecución: Script, línea 4: unexpected EOF while looking for matching `''");
        assertThat(r.salida()).contains("unexpected EOF");
    }

    @Test
    @DisplayName("Sin un error reconocible del script, se informa el código de salida y no el aviso de cierre ni el genérico")
    void sinCausaReconocible() throws Exception {
        ResultadoEjecucion r = fallido(1, "procesando...\n", "Shared connection to 10.0.0.5 closed.\r\n");

        assertThat(r.error()).isEqualTo("Error de ejecución: el script terminó con el código 1");
        assertThat(r.codigoRetorno()).isEqualTo(1);
    }

    @Test
    @DisplayName("Lo que el script escribe por la salida de errores tiene prioridad sobre lo que encuentre en la salida")
    void prioridadDeLaSalidaDeErrores() throws Exception {
        ResultadoEjecucion r = fallido(3, "/tmp/x/tarea.sh: line 9: foo: command not found\n",
                "No existe /var/log/app\nConnection to 10.0.0.5 closed.\n");

        assertThat(r.error()).isEqualTo("Error de ejecución: No existe /var/log/app");
    }
}
