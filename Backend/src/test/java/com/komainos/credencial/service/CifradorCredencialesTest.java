package com.komainos.credencial.service;

import com.komainos.credencial.config.PropiedadesCredenciales;
import com.komainos.credencial.model.SecretoCifrado;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cifrado de credenciales con AES-256-GCM (RNF02-RNF04)")
class CifradorCredencialesTest {

    private static final String LLAVE = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    private final CifradorCredenciales cifrador = new CifradorCredenciales(new PropiedadesCredenciales(LLAVE, 30));

    @Test
    @DisplayName("descifra lo que cifró, sin dejar el secreto en claro en lo que se guarda")
    void idaYVuelta() {
        SecretoCifrado cifrado = cifrador.cifrar("Contraseña con tilde y espacios ", 7);

        assertThat(cifrado.algoritmo()).isEqualTo("AES-256-GCM");
        assertThat(cifrado.iv()).hasSize(12);
        assertThat(cifrado.tag()).hasSize(16);
        assertThat(new String(cifrado.cifrado(), StandardCharsets.UTF_8)).doesNotContain("Contraseña");
        assertThat(cifrador.descifrar(cifrado, 7)).isEqualTo("Contraseña con tilde y espacios ");
    }

    @Test
    @DisplayName("RNF02: cada operación usa un IV distinto, el mismo secreto produce cifrados distintos")
    void ivUnicoPorOperacion() {
        SecretoCifrado primero = cifrador.cifrar("misma-clave", 1);
        SecretoCifrado segundo = cifrador.cifrar("misma-clave", 1);

        assertThat(primero.iv()).isNotEqualTo(segundo.iv());
        assertThat(primero.cifrado()).isNotEqualTo(segundo.cifrado());
    }

    @Test
    @DisplayName("un secreto copiado a otra credencial no se puede descifrar")
    void ligadoALaCredencial() {
        SecretoCifrado cifrado = cifrador.cifrar("clave-de-la-1", 1);

        assertThatThrownBy(() -> cifrador.descifrar(cifrado, 2)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("detecta un dato alterado en la base")
    void detectaAlteracion() {
        SecretoCifrado cifrado = cifrador.cifrar("clave", 1);
        cifrado.cifrado()[0] ^= 1;

        assertThatThrownBy(() -> cifrador.descifrar(cifrado, 1)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("con otra llave maestra el secreto no se puede descifrar")
    void otraLlave() {
        SecretoCifrado cifrado = cifrador.cifrar("clave", 1);
        String otra = Base64.getEncoder().encodeToString("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> new CifradorCredenciales(new PropiedadesCredenciales(otra, 30)).descifrar(cifrado, 1))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("llave maestra");
    }

    @Test
    @DisplayName("RNF03: sin una llave AES-256 válida la aplicación no arranca")
    void exigeLlaveValida() {
        String corta = Base64.getEncoder().encodeToString("corta".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> new CifradorCredenciales(new PropiedadesCredenciales(null, 30)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CifradorCredenciales(new PropiedadesCredenciales(corta, 30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KOMAINOS_CREDENCIALES_LLAVE");
        assertThatThrownBy(() -> new CifradorCredenciales(new PropiedadesCredenciales("no es base64!", 30)))
                .isInstanceOf(IllegalStateException.class);
    }
}
