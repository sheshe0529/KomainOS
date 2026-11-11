package com.komainos.credencial.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Llaves con frase de contraseña: Ansible no puede usarlas sin un agente (DEC-57)")
class LlaveProtegidaTest {

    @Test
    @DisplayName("OpenSSH: el cifrado «none» es una llave sin frase; aes256-ctr, una con frase")
    void openssh() {
        assertThat(ServicioCredenciales.llaveProtegida("""
                -----BEGIN OPENSSH PRIVATE KEY-----
                b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
                -----END OPENSSH PRIVATE KEY-----
                """)).isFalse();
        assertThat(ServicioCredenciales.llaveProtegida("""
                -----BEGIN OPENSSH PRIVATE KEY-----
                b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABAzc2gtZW
                -----END OPENSSH PRIVATE KEY-----
                """)).isTrue();
    }

    @Test
    @DisplayName("PEM: el encabezado ENCRYPTED, tradicional o PKCS#8, indica una llave con frase")
    void pem() {
        assertThat(ServicioCredenciales.llaveProtegida("""
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA
                -----END RSA PRIVATE KEY-----
                """)).isFalse();
        assertThat(ServicioCredenciales.llaveProtegida("""
                -----BEGIN RSA PRIVATE KEY-----
                Proc-Type: 4,ENCRYPTED
                DEK-Info: AES-128-CBC,00
                MIIEowIBAAKCAQEA
                -----END RSA PRIVATE KEY-----
                """)).isTrue();
        assertThat(ServicioCredenciales.llaveProtegida("""
                -----BEGIN ENCRYPTED PRIVATE KEY-----
                MIIFHzBJBgkqhkiG
                -----END ENCRYPTED PRIVATE KEY-----
                """)).isTrue();
    }
}
