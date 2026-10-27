package com.komainos.catalogo.controller;

import com.komainos.DatosPrueba;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.VistaPrevia;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.seguridad.model.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @WebMvcTest no evalúa @PreAuthorize: la autorización por rol se prueba en AutomatizacionIT */
@WebMvcTest({ScriptController.class, TareaController.class})
@Import(ScriptControllerTest.ConfiguracionSeguridadPrueba.class)
@DisplayName("API del catálogo de scripts y tareas (RF22-RF26)")
class ScriptControllerTest {

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {
        @Bean
        SecurityFilterChain cadenaPrueba(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(req -> req.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ServicioCatalogoAutomatizacion servicio;

    @Test
    @DisplayName("CP071: una tarea sin prioridad ni afectación se rechaza indicando los campos faltantes")
    void tareaSinAtributosObligatorios() throws Exception {
        mvc.perform(post("/api/tareas").with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idScript\":1,\"nombre\":\"Liberar temporales\",\"duracionEstimadaMinutos\":5,\"idsUmbrales\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo == 'prioridad')].mensaje").value("Indique la prioridad de la tarea"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'nivelAfectacion')].mensaje").value("Indique la afectación de la tarea"));
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("CP080: una implementación sin código se rechaza antes de llegar al servicio")
    void implementacionSinCodigo() throws Exception {
        mvc.perform(post("/api/scripts/5/implementaciones").with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idVersionSistemaOperativo\":3,\"contenido\":\" \",\"parametros\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("El código del script es obligatorio"));
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("Un nombre de parámetro que no es variable se rechaza: nunca llega a la línea de comandos")
    void parametroInvalido() throws Exception {
        mvc.perform(post("/api/scripts/implementaciones/1/versiones").with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contenido\":\"echo\",\"parametros\":[{\"nombre\":\"x;rm -rf /\",\"tipo\":\"STRING\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("La vista previa devuelve el script preparado, el rango de la cabecera y los problemas del borrador")
    void vistaPrevia() throws Exception {
        when(servicio.vistaPrevia(eq(FamiliaSistemaOperativo.LINUX), any(), anyList()))
                .thenReturn(new VistaPrevia(new ScriptPreparado("tarea.sh", "bash", "#!/bin/bash\nDIAS=7\necho", 2, 2),
                        List.of("El parámetro obligatorio DIAS necesita un valor por defecto")));

        mvc.perform(post("/api/scripts/vista-previa").with(user(DatosPrueba.autenticado(1, "oper", Rol.OPERADOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familia\":\"LINUX\",\"contenido\":\"echo\",\"parametros\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreArchivo").value("tarea.sh"))
                .andExpect(jsonPath("$.lenguaje").value("bash"))
                .andExpect(jsonPath("$.lineaInicioParametros").value(2))
                .andExpect(jsonPath("$.problemas[0]").value("El parámetro obligatorio DIAS necesita un valor por defecto"));
    }

    @Test
    @DisplayName("El listado es paginado y admite filtros")
    void listado() throws Exception {
        when(servicio.scripts(any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/scripts").param("tipo", "ANALISIS").param("texto", "salud")
                        .with(user(DatosPrueba.autenticado(1, "oper", Rol.OPERADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }
}
