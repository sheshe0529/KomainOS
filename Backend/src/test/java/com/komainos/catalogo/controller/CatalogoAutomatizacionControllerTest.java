package com.komainos.catalogo.controller;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.boot.test.context.TestConfiguration;import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Import;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;import org.springframework.security.web.SecurityFilterChain;import org.springframework.test.context.bean.override.mockito.MockitoBean;import org.springframework.test.web.servlet.MockMvc;import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import static org.mockito.Mockito.*;
@WebMvcTest(CatalogoAutomatizacionController.class) @Import(CatalogoAutomatizacionControllerTest.ConfiguracionSeguridadPrueba.class)
class CatalogoAutomatizacionControllerTest {
    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {
        @Bean
        SecurityFilterChain cadenaPrueba(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(req -> req.anyRequest().permitAll())
                    .build();
        }
    }


 @Autowired MockMvc mvc;@MockitoBean ServicioCatalogoAutomatizacion servicio;
 @Test void tareaValidaPrioridadYDuracion()throws Exception{mvc.perform(post("/api/tareas").contentType(MediaType.APPLICATION_JSON).content("{\"idScript\":1,\"nombre\":\"Tarea\",\"nivelAfectacion\":\"SIN_AFECTACION\",\"prioridad\":-1,\"duracionEstimadaMinutos\":0,\"idsUmbrales\":[]}")).andExpect(status().isBadRequest());verifyNoInteractions(servicio);}
 @Test void versionRequiereContenido()throws Exception{mvc.perform(post("/api/scripts/implementaciones/1/versiones").contentType(MediaType.APPLICATION_JSON).content("{\"contenido\":\"\",\"parametros\":[]}")).andExpect(status().isBadRequest());verifyNoInteractions(servicio);}
 @Test void parametrosNoAdmitenNombresDeComandos()throws Exception{mvc.perform(post("/api/scripts/implementaciones/1/versiones").contentType(MediaType.APPLICATION_JSON).content("{\"contenido\":\"echo\",\"parametros\":[{\"nombre\":\"x;comando\",\"tipo\":\"STRING\"}]}")).andExpect(status().isBadRequest());verifyNoInteractions(servicio);}
 @Test void listadoPaginado()throws Exception{when(servicio.scripts(any())).thenReturn(org.springframework.data.domain.Page.empty());mvc.perform(get("/api/scripts")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));}
}