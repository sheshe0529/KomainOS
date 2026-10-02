package com.komainos.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
