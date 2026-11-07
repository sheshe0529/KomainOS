package com.komainos.auditoria.controller;

import com.komainos.auditoria.service.ServicioConsultaAuditoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditoriaController.class)
@Import(AuditoriaControllerTest.SeguridadPrueba.class)
class AuditoriaControllerTest {
    @TestConfiguration
    @EnableMethodSecurity
    static class SeguridadPrueba {
        @Bean
        SecurityFilterChain cadena(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(r -> r.anyRequest().authenticated()).build();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean ServicioConsultaAuditoria consulta;

    @Test
    void soloAdministradorConsultaEventosYFiltros() throws Exception {
        for (String rol : new String[]{"OPERADOR", "RESPONSABLE"}) {
            mvc.perform(get("/api/auditoria").with(user("usuario").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get("/api/auditoria/filtros").with(user("usuario").roles(rol))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(consulta);
        when(consulta.listar(any(), any())).thenReturn(Page.empty());
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void rechazaPeriodoInvertidoAntesDeConsultar() throws Exception {
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMINISTRADOR"))
                        .param("desde", "2026-11-06T00:00:00Z").param("hasta", "2026-11-05T00:00:00Z"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(consulta);
    }
}
