package com.komainos.shared.config;

import com.komainos.PruebaIntegracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El panel compilado se sirve desde el mismo origen que la API (imagen Docker del despliegue). Las pruebas usan el
 * index.html y el archivo de assets de src/test/resources/static en lugar del build de Vite.
 */
@DisplayName("Panel servido por el backend en el despliegue")
class PanelWebIT extends PruebaIntegracion {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Una ruta del panel abierta o recargada directamente responde con index.html sin token")
    void rutaProfunda() throws Exception {
        mvc.perform(get("/inventario/servidores/12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("komainos-panel-prueba")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-cache")));
    }

    @Test
    @DisplayName("Los archivos con hash de assets/ se guardan en caché un año")
    void assetsConCache() throws Exception {
        mvc.perform(get("/assets/panel-prueba.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("komainos-asset-prueba")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")));
    }

    @Test
    @DisplayName("Un archivo inexistente responde 404 y no el panel")
    void archivoInexistente() throws Exception {
        mvc.perform(get("/assets/index-de-otra-version.js")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("La API sigue exigiendo el token y nunca devuelve el panel")
    void apiProtegida() throws Exception {
        mvc.perform(get("/api/servidores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ruta-que-no-existe")).andExpect(status().isUnauthorized());
    }
}
