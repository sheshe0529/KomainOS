package com.komainos.shared.config;

import com.komainos.shared.api.ErrorRespuesta;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

/**
 * El contrato publicado en /v3/api-docs es la fuente desde la que se generan
 * los tipos TypeScript del panel. Mantenerlo fiel a lo que realmente viaja
 * evita que el frontend tipe formas que el backend no devuelve.
 */
@Configuration
public class ConfiguracionOpenApi {

    private static final String ESQUEMA_JWT = "bearerAuth";

    static {
        // Jackson serializa LocalTime como texto "HH:mm"; sin esto el contrato
        // lo describiria como un objeto {hour, minute, ...}.
        SpringDocUtils.getConfig().replaceWithSchema(LocalTime.class,
                new StringSchema().format("time").example("22:00"));
    }

    @Bean
    public OpenAPI openApiKomainos() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de KomainOS")
                        .version("v1")
                        .description("Gestión automatizada del mantenimiento de servidores virtuales"))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * La forma comun de error no la referencia ningun endpoint de forma
     * explicita (la produce el manejador global), pero el cliente del panel la
     * necesita: se publica como esquema del contrato.
     */
    @Bean
    public OpenApiCustomizer esquemaDeErrores() {
        return openApi -> ModelConverters.getInstance().readAll(ErrorRespuesta.class)
                .forEach((nombre, esquema) -> openApi.getComponents().addSchemas(nombre, esquema));
    }
}
