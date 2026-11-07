package com.komainos.auditoria;

import com.komainos.PruebaIntegracion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.model.Actor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuditoriaIT extends PruebaIntegracion {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepositorio usuarios;
    @Autowired ServicioAuditoria auditoria;

    @Test
    void consultaCombinaFiltrosPaginaYValoresProtegidos() throws Exception {
        Usuario admin = usuarios.saveAndFlush(Usuario.nuevo("auditoria-admin-it", "Autora de auditoría", "hash", Rol.ADMINISTRADOR));
        auditoria.registrar(Actor.usuario(admin.getId()), Operacion.de("PRUEBA_AUDITORIA", "prueba_auditoria", 41)
                .motivo("Cambio de nombre").valores(Map.of("nombre", "Anterior"),
                        Map.of("nombre", "Actual", "datos", List.of(Map.of("password", "no-debe-salir")))));
        auditoria.registrar(Actor.sistema("PROCESO_PRUEBA_AUDITORIA"),
                Operacion.de("PRUEBA_AUDITORIA", "prueba_auditoria", 42).motivo("Cambio automático"));
        auditoria.registrar(Actor.usuario(admin.getId()),
                Operacion.de("OTRA_PRUEBA_AUDITORIA", "prueba_auditoria", 43).motivo("Fuera del filtro"));
        usuarios.flush();
        jdbc.update("update auditoria set fecha_hora = ? where entidad = 'prueba_auditoria'",
                Timestamp.from(AHORA));

        String cuerpo = mvc.perform(get("/api/auditoria").with(user(new UsuarioAutenticado(admin)))
                        .param("operacion", "PRUEBA_AUDITORIA").param("entidad", "prueba_auditoria")
                        .param("idUsuario", admin.getId().toString()).param("texto", "Autora")
                        .param("desde", AHORA.toString()).param("hasta", AHORA.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idEntidad").value(41))
                .andExpect(jsonPath("$.contenido[0].valorNuevo.datos[0].password").value("[PROTEGIDO]"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("no-debe-salir", "hashContrasena");
        mvc.perform(get("/api/auditoria").with(user(new UsuarioAutenticado(admin)))
                        .param("operacion", "PRUEBA_AUDITORIA").param("size", "1"))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido[0].idEntidad").value(42));
        mvc.perform(get("/api/auditoria").with(user(new UsuarioAutenticado(admin)))
                        .param("operacion", "PRUEBA_AUDITORIA").param("sistema", "true"))
                .andExpect(jsonPath("$.contenido[0].actor").value("Sistema"))
                .andExpect(jsonPath("$.contenido[0].proceso").value("PROCESO_PRUEBA_AUDITORIA"));
        mvc.perform(get("/api/auditoria").with(user(new UsuarioAutenticado(admin)))
                        .param("operacion", "PRUEBA_AUDITORIA").param("hasta", AHORA.toString()))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/auditoria").with(user(new UsuarioAutenticado(admin)))
                        .param("operacion", "PRUEBA_AUDITORIA").param("texto", "Sistema").param("size", "1000"))
                .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.tamano").value(100));
        mvc.perform(get("/api/auditoria").with(user(new UsuarioAutenticado(admin)))
                        .param("entidad", "prueba_auditoria").param("texto", "%"))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/auditoria/filtros").with(user(new UsuarioAutenticado(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operaciones").isArray());
    }
}
