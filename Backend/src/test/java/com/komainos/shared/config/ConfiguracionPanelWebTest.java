package com.komainos.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rutas que el backend responde con el panel compilado")
class ConfiguracionPanelWebTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "inventario", "inventario/servidores/12", "ordenes/45/seguimiento", "configuracion/mi-cuenta"})
    @DisplayName("Las rutas de React Router responden con index.html")
    void rutasDelPanel(String ruta) {
        assertThat(ConfiguracionPanelWeb.ResolutorPanel.esRutaDelPanel(ruta)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"api/servidores/12", "api/no-existe", "actuator/info", "v3/api-docs", "swagger-ui/index.html",
            "assets/index-viejo.js", "favicon.ico", "inventario/reporte.csv"})
    @DisplayName("La API, Actuator y los archivos inexistentes no devuelven el panel: siguen respondiendo 404")
    void rutasDelServidor(String ruta) {
        assertThat(ConfiguracionPanelWeb.ResolutorPanel.esRutaDelPanel(ruta)).isFalse();
    }
}
