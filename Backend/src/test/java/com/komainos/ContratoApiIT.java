package com.komainos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Deja el contrato en target/openapi.json para generar los tipos del panel sin levantar el backend */
@DisplayName("Contrato OpenAPI del backend")
class ContratoApiIT extends PruebaIntegracion {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("publica el contrato y lo exporta para generar types.ts")
    void exportaContrato() throws Exception {
        String contrato = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(contrato).contains("\"/api/cuentas-servicio\"").doesNotContain("secretoCifrado");
        Files.writeString(Path.of("target", "openapi.json"), contrato, StandardCharsets.UTF_8);
    }
}
