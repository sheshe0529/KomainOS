package com.komainos.inventario.controller;

import com.komainos.DatosPrueba;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.ObjetivoExcepcion;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.Umbral.LimitesNumericos;
import com.komainos.inventario.model.Umbral.ValoresExcepcion;
import com.komainos.inventario.model.UmbralAplicado;
import com.komainos.inventario.service.ServicioListasReferencia;
import com.komainos.inventario.service.ServicioListasReferencia.ListaConUso;
import com.komainos.inventario.service.ServicioMetricas;
import com.komainos.inventario.service.ServicioUmbrales;
import com.komainos.inventario.service.ServicioUmbralesServidor;
import com.komainos.inventario.service.ServicioUmbralesServidor.ResultadoPrueba;
import com.komainos.inventario.service.ServicioUmbralesServidor.UmbralDelServidor;
import com.komainos.seguridad.model.Rol;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** @WebMvcTest no evalúa @PreAuthorize: la autorización por rol se prueba en UmbralesIT */
@WebMvcTest({UmbralController.class, MetricaController.class, ListaReferenciaController.class})
@Import(UmbralControllerTest.ConfiguracionSeguridadPrueba.class)
@DisplayName("API de métricas, listas y umbrales (RF16)")
class UmbralControllerTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

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
    @MockitoBean ServicioUmbrales umbrales;
    @MockitoBean ServicioUmbralesServidor porServidor;
    @MockitoBean ServicioMetricas metricas;
    @MockitoBean ServicioListasReferencia listas;

    private static MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder peticion) {
        return peticion.with(user(DatosPrueba.autenticado(1, "admin", Rol.ADMINISTRADOR)))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static Umbral cpu() {
        return Umbral.numerico(DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false), "CPU alta",
                new LimitesNumericos(OperadorComparacion.MAYOR, new BigDecimal("80.000000"), new BigDecimal("90.500000")),
                AHORA);
    }

    @Test
    @DisplayName("el umbral viaja con sus valores sin los ceros de numeric(20,6) y sus excepciones por prioridad")
    void detalle() throws Exception {
        Umbral umbral = cpu();
        umbral.agregarExcepcion(ObjetivoExcepcion.de(DatosPrueba.criticidad(1, "Alta", 1)),
                new ValoresExcepcion(new BigDecimal("75.000000"), null, null), null, AHORA);
        umbral.agregarExcepcion(ObjetivoExcepcion.de(DatosPrueba.entorno(2, "Producción")),
                new ValoresExcepcion(new BigDecimal("70"), new BigDecimal("85"), null), "Picos de fin de mes", AHORA);
        when(umbrales.obtener(4)).thenReturn(umbral);

        mockMvc.perform(comoAdmin(get("/api/umbrales/4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("CPU alta"))
                .andExpect(jsonPath("$.metrica.codigo").value("cpu_uso_pct"))
                .andExpect(jsonPath("$.operador").value("MAYOR"))
                .andExpect(jsonPath("$.valorAdvertencia").value(80))
                .andExpect(jsonPath("$.valorCritico").value(90.5))
                .andExpect(jsonPath("$.cantidadExcepciones").value(2))
                .andExpect(jsonPath("$.excepciones[0].alcance").value("ENTORNO"))
                .andExpect(jsonPath("$.excepciones[0].objetivo.nombre").value("Producción"))
                .andExpect(jsonPath("$.excepciones[0].motivo").value("Picos de fin de mes"))
                .andExpect(jsonPath("$.excepciones[1].alcance").value("CRITICIDAD"))
                .andExpect(jsonPath("$.excepciones[1].valorCritico").doesNotExist());
    }

    @Test
    @DisplayName("CP186: un código de métrica inválido y un tipo ausente responden 400 en español")
    void validaMetrica() throws Exception {
        mockMvc.perform(comoAdmin(post("/api/metricas")).content("""
                        {"codigo":"9 cpu","nombre":"Uso de CPU","esLista":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'codigo')].mensaje")
                        .value("El código empieza con una letra y solo lleva letras, números y guiones bajos"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'tipoDato')].mensaje")
                        .value("Indique el tipo de dato de la métrica"));
    }

    @Test
    @DisplayName("CP189: una lista sin elementos responde 400 y la respuesta lleva solo los vigentes")
    void listas() throws Exception {
        mockMvc.perform(comoAdmin(post("/api/listas-referencia")).content("""
                        {"nombre":"Puertos web","tipoDato":"INTEGER","elementos":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La lista debe tener al menos un elemento"));

        ListaReferencia web = ListaReferencia.nueva("Puertos web", TipoDato.INTEGER, false, AHORA);
        web.reemplazarElementos(List.of("22", "80", "443"), AHORA);
        web.reemplazarElementos(List.of("22", "443"), AHORA.plusSeconds(60));
        when(listas.listar()).thenReturn(List.of(new ListaConUso(web, 1, 2)));
        mockMvc.perform(comoAdmin(get("/api/listas-referencia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].elementos.length()").value(2))
                .andExpect(jsonPath("$[0].elementos[1]").value("443"))
                .andExpect(jsonPath("$[0].umbrales").value(1))
                .andExpect(jsonPath("$[0].excepciones").value(2));
    }

    @Test
    @DisplayName("un valor de umbral con más de 6 decimales responde 400 y una regla de dominio 422")
    void validaUmbral() throws Exception {
        mockMvc.perform(comoAdmin(post("/api/umbrales")).content("""
                        {"idMetrica":1,"nombre":"CPU alta","operador":"MAYOR","valorAdvertencia":80.1234567}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La advertencia admite hasta 14 enteros y 6 decimales"));

        when(umbrales.crear(any(), any())).thenThrow(new ReglaNegocioException(
                "Los valores no son coherentes con el operador ≥: el crítico debe ser mayor o igual que la advertencia"));
        mockMvc.perform(comoAdmin(post("/api/umbrales")).content("""
                        {"idMetrica":1,"nombre":"Memoria alta","operador":"MAYOR_IGUAL","valorAdvertencia":90,"valorCritico":80}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
    }

    @Test
    @DisplayName("CP193: la ficha del servidor muestra el valor vigente, de dónde sale y el global")
    void delServidor() throws Exception {
        Umbral umbral = cpu();
        Entorno produccion = DatosPrueba.entorno(2, "Producción");
        umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion),
                new ValoresExcepcion(new BigDecimal("70"), new BigDecimal("85"), null), null, AHORA);
        Servidor servidor = DatosPrueba.servidorEn(10, "SERVPRODAPPV01", produccion, DatosPrueba.criticidad(1, "Alta", 1));
        UmbralAplicado aplicado = UmbralAplicado.resolver(umbral, ContextoEvaluacion.individual(servidor));
        when(porServidor.aplicables(eq(10), any())).thenReturn(List.of(new UmbralDelServidor(aplicado, List.of())));

        mockMvc.perform(comoAdmin(get("/api/servidores/10/umbrales")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("ENTORNO"))
                .andExpect(jsonPath("$[0].excepcion.objetivo.nombre").value("Producción"))
                .andExpect(jsonPath("$[0].aplicado.valorCritico").value(85))
                .andExpect(jsonPath("$[0].global.valorCritico").value(90.5));
    }

    @Test
    @DisplayName("CP197: probar un valor devuelve el nivel sin registrar; un servidor ajeno responde 404")
    void prueba() throws Exception {
        UmbralAplicado aplicado = UmbralAplicado.global(cpu());
        when(porServidor.probar(eq(10), eq(4), any(), isNull(), isNull(), any()))
                .thenReturn(new ResultadoPrueba(aplicado, NivelAlerta.ADVERTENCIA, new BigDecimal("85"), List.of(), List.of()));
        mockMvc.perform(comoAdmin(get("/api/servidores/10/umbrales/4/prueba").param("valor", "85")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.origen").value("GLOBAL"))
                .andExpect(jsonPath("$.faltantes").doesNotExist());

        when(porServidor.probar(eq(11), eq(4), any(), any(), any(), any()))
                .thenThrow(RecursoNoEncontradoException.de("el servidor", 11));
        mockMvc.perform(comoAdmin(get("/api/servidores/11/umbrales/4/prueba").param("valor", "85")))
                .andExpect(status().isNotFound());
        mockMvc.perform(comoAdmin(get("/api/servidores/10/umbrales/4/prueba").param("valor", "ochenta")))
                .andExpect(status().isBadRequest());
    }
}
