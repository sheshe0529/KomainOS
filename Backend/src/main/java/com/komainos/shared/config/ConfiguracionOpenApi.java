package com.komainos.shared.config;

import com.komainos.shared.dto.ErrorRespuesta;
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

/** El contrato de /v3/api-docs genera los tipos TypeScript del panel: debe ser fiel a lo que viaja */
@Configuration
public class ConfiguracionOpenApi {

    private static final String ESQUEMA_JWT = "bearerAuth";

    static {
        // Jackson serializa LocalTime como "HH:mm", sin esto el contrato lo describiría como objeto
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

    /** La forma común de error la produce el manejador global: se publica para que el panel la tipe */
    @Bean
    public OpenApiCustomizer esquemaDeErrores() {
        return openApi -> ModelConverters.getInstance().readAll(ErrorRespuesta.class)
                .forEach((nombre, esquema) -> openApi.getComponents().addSchemas(nombre, esquema));
    }
}
