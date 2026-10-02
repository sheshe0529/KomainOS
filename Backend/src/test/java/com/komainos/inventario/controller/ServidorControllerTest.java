package com.komainos.inventario.controller;

import com.komainos.DatosPrueba;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.exception.ConflictoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @WebMvcTest no evalúa @PreAuthorize: la autorización por rol se prueba en integración */
@WebMvcTest(ServidorController.class)
@Import(ServidorControllerTest.ConfiguracionSeguridadPrueba.class)
@DisplayName("API del inventario de servidores")
class ServidorControllerTest {

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {
        @Bean
        SecurityFilterChain cadenaPrueba(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(req -> req.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean ServicioServidor servicio;

    private final Usuario responsable = DatosPrueba.usuario(5, "m.herrera", Rol.RESPONSABLE);

    @Test
    @DisplayName("el listado devuelve la página con referencias legibles y sin datos sensibles")
    void listado() throws Exception {
        Servidor servidor = DatosPrueba.servidor(1, "srv-app-01", responsable);
        servidor.setFechaActualizacion(Instant.parse("2026-09-27T12:00:00Z"));
        when(servicio.listar(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(servidor), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/servidores")
                        .with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].hostname").value("srv-app-01"))
                .andExpect(jsonPath("$.contenido[0].sistemaOperativo.nombre").value("Ubuntu"))
                .andExpect(jsonPath("$.contenido[0].versionSistemaOperativo.nombre").value("Ubuntu 22.04"))
                .andExpect(jsonPath("$.contenido[0].criticidad.nombre").value("Alta"))
                .andExpect(jsonPath("$.contenido[0].responsable.nombre").value("Usuario m.herrera"))
                .andExpect(jsonPath("$.contenido[0].estado").value("PENDIENTE_DE_CONFIGURACION"))
                // El hash de la contraseña del responsable nunca debe viajar (RNF11)
                .andExpect(jsonPath("$..hashContrasena").doesNotExist());
    }

    @Test
    @DisplayName("una IP mal formada responde 400 con el mensaje en español junto al campo")
    void validaIp() throws Exception {
        String cuerpo = """
                {"hostname":"srv-app-09","direccionIp":"10.20.1.999","idVersionSistemaOperativo":1,
                 "idEntorno":1,"idNivelCriticidad":1,"idResponsable":5}
                """;
        mockMvc.perform(post("/api/servidores").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("direccionIp"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La dirección IP no tiene un formato IPv4 o IPv6 válido"));
    }

    @Test
    @DisplayName("un hostname duplicado responde 409 CONFLICTO")
    void duplicado() throws Exception {
        when(servicio.crear(any(), any())).thenThrow(new ConflictoException("Ya existe un servidor con el hostname srv-app-01"));
        String cuerpo = """
                {"hostname":"srv-app-01","direccionIp":"10.20.1.11","idVersionSistemaOperativo":1,
                 "idEntorno":1,"idNivelCriticidad":1,"idResponsable":5}
                """;
        mockMvc.perform(post("/api/servidores").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe un servidor con el hostname srv-app-01"));
    }

    @Test
    @DisplayName("un día de ventana inexistente responde 400 y no 500")
    void enumInvalido() throws Exception {
        String cuerpo = """
                {"ventanas":[{"diaInicio":"FERIADO","horaInicio":"01:00","diaFin":"SABADO","horaFin":"05:00"}]}
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/servidores/1/ventanas")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
}
