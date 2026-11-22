package com.komainos.ejecucion.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Limpia la salida remota antes de persistirla o mostrarla (RNF11): los secretos conocidos de la cuenta, las llaves
 * privadas y cualquier asignación con aspecto de contraseña o token. También acota el tamaño guardado.
 */
public final class SanitizadorSalida {

    public static final int LARGO_MAXIMO = 64_000;
    private static final String PROTEGIDO = "[PROTEGIDO]";
    /**
     * Una asignación sensible: password=…, "token": …, DB_PASSWORD=…, db.password: …, --password=…, X-Auth-Token: … La
     * clave empieza donde puede empezar una clave (al inicio de la línea o tras un espacio o { [ ( , ; & ? | $) y termina
     * en el nombre sensible. Así no se toma por clave una ruta (/etc/passwd:644, /etc/security/opasswd:600), el valor de
     * otra asignación (OBSERVADO=root_contrasena:no_bloqueada), un elemento de una lista JSON
     * ("root_contrasena:bloqueada") ni un nombre que solo contiene la palabra (LocalAccountTokenFilterPolicy=1)
     */
    private static final Pattern ASIGNACION_SENSIBLE = Pattern.compile(
            "(?imu)(?:^|(?<=[\\s{\\[(,;&?|$]))"
                    + "(?<asignacion>(?<comilla>[\"']?)(?:[\\p{L}\\p{N}_.\\-]*(?:password|passwd|contrasena|contraseña|"
                    + "secreto|secret|token)|ansible_[a-z_]*pass[a-z_]*)\\k<comilla>\\h*[:=]\\h*)"
                    + "(?<valor>\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"
                    + "|\\[PROTEGIDO\\]|[^\\s,}\\]\"']+)");

    private SanitizadorSalida() {
    }

    public static String limpiar(String texto, String... secretos) {
        String s = terminal(texto == null ? "" : texto);
        for (String secreto : secretos) {
            if (secreto != null && !secreto.isBlank()) {
                s = s.replace(secreto, PROTEGIDO);
            }
        }
        s = s.replaceAll("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----", "[LLAVE PROTEGIDA]");
        s = ASIGNACION_SENSIBLE.matcher(s).replaceAll(coincidencia -> {
            String valor = coincidencia.group("valor");
            String comilla = switch (valor.charAt(0)) {
                case '"' -> "\"";
                case '\'' -> "'";
                default -> coincidencia.group("comilla");
            };
            // En JSON, un secreto numérico o booleano se convierte en una cadena protegida válida.
            return Matcher.quoteReplacement(coincidencia.group("asignacion") + comilla + PROTEGIDO + comilla);
        });
        return acotar(s, LARGO_MAXIMO);
    }

    /**
     * Quita lo que deja la terminal con la que Ansible eleva privilegios (DEC-57): secuencias de control (por ejemplo,
     * la OSC 3008 que emite su en Ubuntu 26.04), los asteriscos que sudo-rs muestra al recibir la contraseña y luego
     * borra con retrocesos, y los retornos de carro
     */
    static String terminal(String texto) {
        String s = texto.replaceAll("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)", "")
                .replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "")
                .replace("\r\n", "\n")
                .replace("\r", "");
        if (s.indexOf('\b') < 0) {
            return s;
        }
        StringBuilder limpio = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c != '\b') {
                limpio.append(c);
            } else if (!limpio.isEmpty() && limpio.charAt(limpio.length() - 1) != '\n') {
                limpio.setLength(limpio.length() - 1);
            }
        }
        return limpio.toString();
    }

    public static String acotar(String texto, int largo) {
        if (texto == null || texto.length() <= largo) {
            return texto;
        }
        return texto.substring(0, largo) + "\n… [salida truncada: se conservan los primeros %d caracteres]".formatted(largo);
    }
}
