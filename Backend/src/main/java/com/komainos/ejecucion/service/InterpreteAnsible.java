package com.komainos.ejecucion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.shared.exception.ReglaNegocioException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Traduce lo que escribió ansible-playbook a un {@link ResultadoEjecucion}. El callback komainos_json emite una línea
 * {@code KOMAINOS_JSON=} con el estado de la tarea; si falta, el error ocurrió antes de ejecutar (sintaxis, módulos o
 * dependencias del controlador) y se rescatan las líneas ERROR del registro. Los mensajes conocidos se clasifican para
 * que RF71 distinga comunicación, autenticación y permisos, con una indicación de cómo resolverlos.
 */
public final class InterpreteAnsible {

    public static final String PREFIJO = "KOMAINOS_JSON=";
    /** El script de comprobación de acceso termina con este código si la cuenta no es administradora */
    public static final int CODIGO_SIN_PERMISOS = 3;
    private static final int LARGO_DETALLE = 1500;

    private static final List<String> SENALES_AUTENTICACION = List.of(
            "permission denied (publickey", "permission denied, please try again", "authentication failed",
            "invalid credentials", "credentials were rejected", "unauthorized", "401", "logon failure",
            "incorrect password", "the username or password is incorrect", "kerberos", "no credentials");
    private static final List<String> SENALES_PERMISOS = List.of(
            "incorrect su password", "su: authentication failure", "waiting for privilege escalation prompt",
            "missing sudo password", "a password is required", "is not in the sudoers file",
            "authentication or permission failure", "access is denied", "access denied", "requires elevation");
    private static final List<String> SENALES_CONFIGURACION = List.of(
            "couldn't resolve module/action", "no module named", "winrm or requests is not installed",
            "requires the pywinrm", "to use the 'ssh' connection type with passwords", "sshpass",
            "the field 'hosts' has an invalid value", "syntax error", "requires the locale encoding");

    private InterpreteAnsible() {
    }

    public static ResultadoEjecucion interpretar(String registro, int codigoProceso, PropositoEjecucion proposito,
                                                 ObjectMapper json, String... secretos) {
        Optional<JsonNode> estado = lineaResultado(registro, json);
        if (estado.isEmpty()) {
            String detalle = lineasError(registro);
            TipoFallo tipo = clasificar(detalle).orElse(TipoFallo.CONFIGURACION);
            String mensaje = detalle.isBlank()
                    ? "ansible-playbook terminó con el código %d sin informar un resultado".formatted(codigoProceso)
                    : detalle;
            return ResultadoEjecucion.fallo(false, tipo, limpiar(conIndicacion(tipo, mensaje), secretos));
        }
        JsonNode r = estado.get();
        String resultado = r.path("estado").asText("failed");
        String stdout = SanitizadorSalida.limpiar(r.path("stdout").asText(""), secretos);
        String stderr = r.path("stderr").asText("");
        String msg = r.path("msg").isTextual() ? r.path("msg").asText() : r.path("msg").toString();
        String detalle = primeroNoVacio(stderr, msg);

        if ("unreachable".equals(resultado)) {
            TipoFallo tipo = clasificar(detalle).orElse(TipoFallo.COMUNICACION);
            return ResultadoEjecucion.fallo(false, tipo, limpiar(conIndicacion(tipo, detalle), secretos));
        }
        if ("ok".equals(resultado)) {
            return ResultadoEjecucion.exito(false, stdout, List.of());
        }
        if (r.path("rc").isInt()) {
            int codigo = r.path("rc").asInt();
            if (proposito == PropositoEjecucion.CONECTIVIDAD && codigo == CODIGO_SIN_PERMISOS) {
                return ResultadoEjecucion.fallo(false, TipoFallo.PERMISOS, limpiar(detalle, secretos));
            }
            Optional<TipoFallo> escalamiento = clasificar(detalle).filter(t -> t == TipoFallo.PERMISOS);
            if (escalamiento.isPresent()) {
                return ResultadoEjecucion.fallo(false, TipoFallo.PERMISOS,
                        limpiar(conIndicacion(TipoFallo.PERMISOS, detalle), secretos));
            }
            return ResultadoEjecucion.codigoError(false, codigo, stdout, limpiar(detalle, secretos));
        }
        TipoFallo tipo = clasificar(detalle).orElse(TipoFallo.EJECUCION);
        return ResultadoEjecucion.fallo(false, tipo, limpiar(conIndicacion(tipo, detalle), secretos));
    }

    /** Categoría de un mensaje de Ansible o del sistema remoto, si se reconoce */
    public static Optional<TipoFallo> clasificar(String mensaje) {
        String m = mensaje == null ? "" : mensaje.toLowerCase(Locale.ROOT);
        if (m.isBlank()) {
            return Optional.empty();
        }
        // Límite de la palabra clave timeout de la tarea: no es un problema de red aunque diga «timed out»
        if (m.contains("expected time frame") || m.contains("task timed out")) {
            return Optional.of(TipoFallo.TIEMPO_EXCEDIDO);
        }
        if (contiene(m, SENALES_CONFIGURACION)) {
            return Optional.of(TipoFallo.CONFIGURACION);
        }
        if (contiene(m, SENALES_PERMISOS)) {
            return Optional.of(TipoFallo.PERMISOS);
        }
        if (contiene(m, SENALES_AUTENTICACION)) {
            return Optional.of(TipoFallo.AUTENTICACION);
        }
        if (m.contains("timed out") || m.contains("connection refused") || m.contains("no route to host")
                || m.contains("could not resolve") || m.contains("name or service not known")
                || m.contains("network is unreachable") || m.contains("host key verification failed")
                || m.contains("certificate verify failed") || m.contains("max retries exceeded")
                || m.contains("connection reset") || m.contains("failed to connect")) {
            return Optional.of(TipoFallo.COMUNICACION);
        }
        return Optional.empty();
    }

    /** Agrega cómo resolver los casos de laboratorio más frecuentes */
    static String conIndicacion(TipoFallo tipo, String detalle) {
        String m = detalle == null ? "" : detalle.toLowerCase(Locale.ROOT);
        String indicacion = null;
        if (m.contains("host key verification failed")) {
            indicacion = "Registre la clave del servidor en el known_hosts del controlador (ssh-keyscan) o, solo en "
                    + "laboratorio, desactive la verificación con KOMAINOS_ANSIBLE_SSH_VERIFICAR_CLAVE_HOST=false.";
        } else if (m.contains("certificate verify failed")) {
            indicacion = "El certificado WinRM no es de confianza para el controlador: instale su CA o, solo en "
                    + "laboratorio, use KOMAINOS_ANSIBLE_WINRM_VALIDAR_CERTIFICADO=false.";
        } else if (m.contains("winrm or requests is not installed") || m.contains("requires the pywinrm")) {
            indicacion = "Instale pywinrm en el controlador: pip install pywinrm.";
        } else if (m.contains("sshpass")) {
            indicacion = "Las cuentas con contraseña por SSH requieren sshpass en el controlador.";
        } else if (m.contains("couldn't resolve module/action")) {
            indicacion = "Instale la colección indicada en el controlador (ansible-galaxy collection install).";
        } else if (tipo == TipoFallo.COMUNICACION && m.contains("timed out")) {
            indicacion = "Compruebe la IP, el puerto (SSH 22 o WinRM 5986) y el cortafuegos.";
        }
        String base = detalle == null ? "" : detalle.trim();
        if (indicacion == null) {
            return base;
        }
        return base.isEmpty() ? indicacion : base + " — " + indicacion;
    }

    private static Optional<JsonNode> lineaResultado(String registro, ObjectMapper json) {
        if (registro == null) {
            return Optional.empty();
        }
        return registro.lines()
                .filter(l -> l.startsWith(PREFIJO))
                .reduce((primera, ultima) -> ultima)
                .flatMap(l -> {
                    try {
                        return Optional.ofNullable(json.readTree(l.substring(PREFIJO.length())));
                    } catch (JsonProcessingException ex) {
                        throw new ReglaNegocioException("El resultado estructurado de Ansible no es JSON válido");
                    }
                });
    }

    /** Las líneas ERROR! o fatal: de ansible-playbook, o el final del registro si no hay ninguna */
    private static String lineasError(String registro) {
        if (registro == null || registro.isBlank()) {
            return "";
        }
        List<String> lineas = registro.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        List<String> errores = lineas.stream()
                .filter(l -> l.startsWith("ERROR!") || l.startsWith("fatal:") || l.startsWith("[ERROR]"))
                .toList();
        List<String> elegidas = errores.isEmpty() ? lineas.subList(Math.max(0, lineas.size() - 5), lineas.size()) : errores;
        return String.join(" ", elegidas);
    }

    private static String limpiar(String texto, String... secretos) {
        return SanitizadorSalida.acotar(SanitizadorSalida.limpiar(texto, secretos), LARGO_DETALLE);
    }

    private static boolean contiene(String mensaje, List<String> senales) {
        return senales.stream().anyMatch(mensaje::contains);
    }

    private static String primeroNoVacio(String... textos) {
        for (String t : textos) {
            if (t != null && !t.isBlank()) {
                return t.strip();
            }
        }
        return "";
    }
}
