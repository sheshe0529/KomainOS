package com.komainos.seguridad;

import com.jayway.jsonpath.JsonPath;
import com.komainos.PruebaIntegracion;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin @Transactional: el filtro JWT carga al usuario desde la base, y el conteo de administradores debe ser exacto */
@DisplayName("Sesiones y cuentas de administrador contra PostgreSQL (RF01-RF03, RNF01)")
class AutenticacionIT extends PruebaIntegracion {

    @Autowired MockMvc mockMvc;
    @Autowired ServicioUsuario usuarios;

    private final Actor sistema = Actor.sistema("PRUEBA");
    private Usuario admin;

    @BeforeEach
    void preparar() {
        limpiarDatos();
        admin = usuarios.crear("admin.sesion", "Administrador", "clave-segura-1", Rol.ADMINISTRADOR, sistema);
    }

    /** Cada prueba usa su propio origen: el límite por origen no se mezcla con el de otras pruebas */
    private ResultActions login(String codigo, String contrasena, String origen) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(peticion -> {
                    peticion.setRemoteAddr(origen);
                    return peticion;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"%s\",\"contrasena\":\"%s\"}".formatted(codigo, contrasena)));
    }

    private String token(String codigo, String contrasena, String origen) throws Exception {
        String cuerpo = login(codigo, contrasena, origen).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private ResultActions yo(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    @DisplayName("CP205: tras cinco fallos la cuenta responde 429 con Retry-After, aun con la contraseña correcta")
    void limiteDeIntentos() throws Exception {
        usuarios.crear("oper.sesion", "Operador", "clave-segura-2", Rol.OPERADOR, sistema);
        for (int i = 0; i < 5; i++) {
            login("oper.sesion", "incorrecta", "10.9.0.1").andExpect(status().isUnauthorized());
        }

        login("oper.sesion", "clave-segura-2", "10.9.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
        // El bloqueo es de esa cuenta desde ese origen: otra cuenta del mismo origen sigue entrando
        login("admin.sesion", "clave-segura-1", "10.9.0.1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("CP206: cerrar sesión invalida el token en el servidor, aunque no haya vencido")
    void cierreDeSesion() throws Exception {
        String token = token("admin.sesion", "clave-segura-1", "10.9.0.2");
        yo(token).andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("admin.sesion"));

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        yo(token).andExpect(status().isUnauthorized());
        // Sin token el cierre no falla: no hay nada que revocar
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("cambiar el rol de una cuenta cierra sus sesiones: el token con el rol anterior deja de valer")
    void cambioDeRolRevoca() throws Exception {
        Usuario operador = usuarios.crear("oper2.sesion", "Operador", "clave-segura-3", Rol.OPERADOR, sistema);
        String token = token("oper2.sesion", "clave-segura-3", "10.9.0.3");
        yo(token).andExpect(status().isOk());

        usuarios.actualizar(operador.getId(), "Operador", Rol.RESPONSABLE, Actor.usuario(admin.getId()));

        yo(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CP207: siempre queda un administrador activo, y nadie se desactiva ni se degrada a sí mismo")
    void ultimoAdministrador() {
        assertThatThrownBy(() -> usuarios.desactivar(admin.getId(), Actor.usuario(admin.getId())))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pídalo a otro administrador");
        assertThatThrownBy(() -> usuarios.actualizar(admin.getId(), "Administrador", Rol.OPERADOR,
                Actor.usuario(admin.getId())))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("su propia cuenta");
        assertThatThrownBy(() -> usuarios.desactivar(admin.getId(), sistema))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("único administrador activo");

        Usuario otro = usuarios.crear("admin2.sesion", "Otro administrador", "clave-segura-4", Rol.ADMINISTRADOR, sistema);
        usuarios.desactivar(admin.getId(), Actor.usuario(otro.getId()));

        assertThat(jdbc.queryForObject("select count(*) from usuario where rol = 'ADMINISTRADOR' and activo",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("una contraseña de más de 72 bytes se rechaza al crear la cuenta, contando cada ñ como dos")
    void contrasenaLarga() throws Exception {
        String cuerpo = """
                {"codigo":"largo.sesion","nombreCompleto":"Largo","rol":"OPERADOR","contrasena":"%s"}"""
                .formatted("ñ".repeat(37));
        String token = token("admin.sesion", "clave-segura-1", "10.9.0.4");

        mockMvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.containsString("72 bytes")));
    }
}
