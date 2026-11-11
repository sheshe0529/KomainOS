package com.komainos.ejecucion.service;

/**
 * Limpia la salida remota antes de persistirla o mostrarla (RNF11): los secretos conocidos de la cuenta, las llaves
 * privadas y cualquier asignación con aspecto de contraseña o token. También acota el tamaño guardado.
 */
public final class SanitizadorSalida {

    public static final int LARGO_MAXIMO = 64_000;
    private static final String PROTEGIDO = "[PROTEGIDO]";

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
        s = s.replaceAll("(?im)(password|passwd|contrasena|contraseña|secreto|secret|token|ansible_[a-z_]*pass[a-z_]*)"
                + "(\"?\\s*[:=]\\s*)[^\\s,}\\r\\n]+", "$1$2" + PROTEGIDO);
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
