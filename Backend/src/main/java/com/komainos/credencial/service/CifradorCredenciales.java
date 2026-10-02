package com.komainos.credencial.service;

import com.komainos.credencial.config.PropiedadesCredenciales;
import com.komainos.credencial.model.SecretoCifrado;
import com.komainos.shared.exception.ReglaNegocioException;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** AES-256-GCM con un IV aleatorio por operación: repetirlo con la misma llave rompería el cifrado (RNF02) */
@Component
public class CifradorCredenciales {

    public static final String ALGORITMO = "AES-256-GCM";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int BYTES_LLAVE = 32;
    private static final int BYTES_IV = 12;
    private static final int BITS_TAG = 128;

    private final SecretKey llave;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorCredenciales(PropiedadesCredenciales propiedades) {
        this.llave = llaveDe(propiedades.llaveMaestra());
    }

    /** Sin una llave válida la aplicación no arranca, en vez de cifrar con una llave débil o conocida (RNF03) */
    static SecretKey llaveDe(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException ex) {
            throw llaveInvalida();
        }
        if (bytes.length != BYTES_LLAVE) {
            throw llaveInvalida();
        }
        return new SecretKeySpec(bytes, "AES");
    }

    /** El id de la credencial va como dato asociado: un secreto copiado a otra fila no se puede descifrar */
    public SecretoCifrado cifrar(String secreto, Integer idCredencial) {
        byte[] iv = new byte[BYTES_IV];
        aleatorio.nextBytes(iv);
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, iv));
            cifrador.updateAAD(contexto(idCredencial));
            byte[] salida = cifrador.doFinal(secreto.getBytes(StandardCharsets.UTF_8));
            int corte = salida.length - BITS_TAG / 8;
            return new SecretoCifrado(Arrays.copyOfRange(salida, 0, corte), iv,
                    Arrays.copyOfRange(salida, corte, salida.length), ALGORITMO);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el secreto", ex);
        }
    }

    public String descifrar(SecretoCifrado secreto, Integer idCredencial) {
        if (!ALGORITMO.equals(secreto.algoritmo())) {
            throw new IllegalStateException("Algoritmo de cifrado no soportado: " + secreto.algoritmo());
        }
        byte[] entrada = new byte[secreto.cifrado().length + secreto.tag().length];
        System.arraycopy(secreto.cifrado(), 0, entrada, 0, secreto.cifrado().length);
        System.arraycopy(secreto.tag(), 0, entrada, secreto.cifrado().length, secreto.tag().length);
        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, secreto.iv()));
            cifrador.updateAAD(contexto(idCredencial));
            return new String(cifrador.doFinal(entrada), StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new ReglaNegocioException("No se pudo descifrar el secreto: la llave maestra no es la que se usó "
                    + "al registrarlo o el dato fue alterado");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar el secreto", ex);
        }
    }

    private static byte[] contexto(Integer idCredencial) {
        return ("komainos:credencial:" + idCredencial).getBytes(StandardCharsets.UTF_8);
    }

    private static IllegalStateException llaveInvalida() {
        return new IllegalStateException(
                "KOMAINOS_CREDENCIALES_LLAVE debe ser una llave AES-256 en base64 (32 bytes): openssl rand -base64 32");
    }
}
