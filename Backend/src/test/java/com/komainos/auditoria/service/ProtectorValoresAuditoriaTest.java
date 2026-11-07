package com.komainos.auditoria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectorValoresAuditoriaTest {
    private final ProtectorValoresAuditoria protector = new ProtectorValoresAuditoria(new ObjectMapper());

    @Test
    @DisplayName("RNF11: protege campos sensibles también dentro de listas y conserva los datos de negocio")
    void protegeValoresAnidados() {
        var valor = protector.leer("""
                {"nombre":"Cuenta Linux","hash_contrasena":"hash-ficticio", "datos":[
                    {"password":"texto-ficticio", "idCuentaServicio":9, "secretoSu":"su-ficticio"}]}
                """);
        assertThat(valor.toString()).doesNotContain("hash-ficticio", "texto-ficticio", "su-ficticio");
        assertThat(valor.path("nombre").asText()).isEqualTo("Cuenta Linux");
        assertThat(valor.path("datos").get(0).path("idCuentaServicio").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("Un valor legado ilegible no devuelve el texto original")
    void noExponeTextoIlegible() {
        assertThat(protector.leer("password=texto-ficticio").toString()).doesNotContain("texto-ficticio");
        assertThat(protector.leer(null)).isNull();
    }
}
