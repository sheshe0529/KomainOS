package com.komainos.seguridad.config;

import com.komainos.shared.validation.ValidadorMaximoBytes;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt solo usa los primeros 72 bytes. Spring Security 6.5 ya rechaza codificar una contraseña más larga, pero al
 * comparar sigue aceptando una entrada que comparta esos 72 bytes (CVE-2025-22228): aquí esa entrada no coincide nunca,
 * porque ninguna contraseña guardada puede superar el límite.
 */
public class CodificadorContrasena implements PasswordEncoder {

    public static final int MAXIMO_BYTES = 72;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence contrasena) {
        if (excede(contrasena)) {
            throw new IllegalArgumentException("La contraseña supera los %d bytes".formatted(MAXIMO_BYTES));
        }
        return bcrypt.encode(contrasena);
    }

    @Override
    public boolean matches(CharSequence contrasena, String hash) {
        return contrasena != null && !excede(contrasena) && bcrypt.matches(contrasena, hash);
    }

    @Override
    public boolean upgradeEncoding(String hash) {
        return bcrypt.upgradeEncoding(hash);
    }

    public static boolean excede(CharSequence contrasena) {
        return contrasena != null && ValidadorMaximoBytes.bytes(contrasena.toString()) > MAXIMO_BYTES;
    }
}
