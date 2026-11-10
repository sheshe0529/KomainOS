package com.komainos.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Sirve el panel compilado desde el mismo origen que la API, como en la imagen Docker del despliegue: el navegador no
 * necesita CORS y el token nunca viaja a otro dominio. Las rutas del panel que no son archivos (por ejemplo
 * /inventario/servidores/12) responden con index.html para que React Router las resuelva al recargar la página.
 * Si el JAR no trae el panel (en desarrollo lo sirve Vite), no registra nada.
 */
@Configuration
public class ConfiguracionPanelWeb implements WebMvcConfigurer {

    static final String UBICACION = "classpath:/static/";

    /** Prefijos que nunca devuelven el panel: una ruta inexistente de la API sigue respondiendo 404 */
    private static final List<String> PREFIJOS_SERVIDOR = List.of("api/", "actuator/", "v3/", "swagger-ui");

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registro) {
        if (!new ClassPathResource("static/index.html").exists()) {
            return;
        }
        // Vite agrega un hash al nombre de cada archivo de assets/: su contenido nunca cambia
        registro.addResourceHandler("/assets/**")
                .addResourceLocations(UBICACION + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // index.html se revalida siempre, así una versión nueva del panel llega sin borrar la caché del navegador
        registro.addResourceHandler("/**")
                .addResourceLocations(UBICACION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new ResolutorPanel());
    }

    /** Primero el archivo pedido, con las comprobaciones de ruta de Spring; si no existe y es una ruta del panel, index.html */
    static final class ResolutorPanel extends PathResourceResolver {

        @Override
        protected Resource getResource(@NonNull String ruta, @NonNull Resource ubicacion) throws IOException {
            Resource archivo = super.getResource(ruta, ubicacion);
            if (archivo != null) {
                return archivo;
            }
            return esRutaDelPanel(ruta) ? super.getResource("index.html", ubicacion) : null;
        }

        static boolean esRutaDelPanel(String ruta) {
            if (PREFIJOS_SERVIDOR.stream().anyMatch(ruta::startsWith)) {
                return false;
            }
            // Un archivo que no existe (favicon.ico, un .js de otra versión) responde 404 y no el panel
            String ultimo = ruta.substring(ruta.lastIndexOf('/') + 1);
            return !ultimo.contains(".");
        }
    }
}
