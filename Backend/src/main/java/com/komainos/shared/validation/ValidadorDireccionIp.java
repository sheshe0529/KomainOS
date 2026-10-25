package com.komainos.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Una IPv6 solo pasa a InetAddress si es un literal hexadecimal con dos puntos: así nunca consulta DNS */
public class ValidadorDireccionIp implements ConstraintValidator<DireccionIp, String> {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern CARACTERES_IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null || valor.isBlank()) {
            return true; // la obligatoriedad la verifica @NotBlank
        }
        return esValida(valor);
    }

    /**
     * Una sola forma por dirección para que la unicidad no dependa de cómo se escribió: 2001:0DB8:0:0:0:0:0:1 y
     * 2001:db8::1 son la misma. IPv6 se escribe según RFC 5952 (minúsculas, sin ceros a la izquierda y el tramo de ceros
     * más largo como ::); IPv4 queda igual. Un texto inválido vuelve recortado y en minúsculas, para que la validación
     * lo informe tal como llegó.
     */
    public static String canonica(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim().toLowerCase(Locale.ROOT);
        if (!esValida(texto) || IPV4.matcher(texto).matches()) {
            return texto;
        }
        try {
            return rfc5952(InetAddress.getByName(texto).getAddress());
        } catch (UnknownHostException ex) {
            return texto;
        }
    }

    private static String rfc5952(byte[] bytes) {
        int[] grupos = new int[8];
        for (int i = 0; i < 8; i++) {
            grupos[i] = ((bytes[2 * i] & 0xff) << 8) | (bytes[2 * i + 1] & 0xff);
        }
        // El tramo de ceros más largo, de al menos dos grupos; ante un empate, el primero
        int inicio = -1;
        int largo = 0;
        for (int i = 0; i < 8; ) {
            if (grupos[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && grupos[j] == 0) {
                j++;
            }
            if (j - i > largo && j - i >= 2) {
                inicio = i;
                largo = j - i;
            }
            i = j;
        }
        StringBuilder salida = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == inicio) {
                salida.append("::");
                i += largo - 1;
                continue;
            }
            if (salida.length() > 0 && salida.charAt(salida.length() - 1) != ':') {
                salida.append(':');
            }
            salida.append(Integer.toHexString(grupos[i]));
        }
        return salida.toString();
    }

    /** La misma regla fuera de Bean Validation, por ejemplo al importar (RF12) */
    public static boolean esValida(String valor) {
        if (valor == null) {
            return false;
        }
        String texto = valor.trim();
        if (IPV4.matcher(texto).matches()) {
            return true;
        }
        if (!texto.contains(":") || !CARACTERES_IPV6.matcher(texto).matches()) {
            return false;
        }
        try {
            return InetAddress.getByName(texto) instanceof java.net.Inet6Address;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
