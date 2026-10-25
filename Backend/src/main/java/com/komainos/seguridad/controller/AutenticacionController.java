package com.komainos.seguridad.controller;

import com.komainos.seguridad.dto.LoginPeticion;
import com.komainos.seguridad.dto.PerfilRespuesta;
import com.komainos.seguridad.dto.TokenRespuesta;
import com.komainos.seguridad.dto.UsuarioSesion;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.service.PuertoParametrosSesion;
import com.komainos.seguridad.service.PuertoServidoresACargo;
import com.komainos.seguridad.service.ServicioAutenticacion;
import com.komainos.seguridad.service.ServicioAutenticacion.Sesion;
import com.komainos.seguridad.service.ServicioUsuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
public class AutenticacionController {

    private static final String PREFIJO = "Bearer ";

    private final ServicioAutenticacion autenticacion;
    private final PuertoParametrosSesion parametrosSesion;
    private final ServicioUsuario servicioUsuario;
    private final PuertoServidoresACargo servidoresACargo;

    /**
     * Credenciales inválidas o cuenta inactiva llegan como 401 genérico: no se distingue usuario inexistente de contraseña
     * incorrecta (HU01). Tras varios fallos responde 429 hasta que pase la espera. El origen es la IP de la conexión: detrás
     * de un proxy, este debe conservarla o aplicar su propio límite.
     */
    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve el token de acceso")
    public TokenRespuesta login(@Valid @RequestBody LoginPeticion peticion, HttpServletRequest solicitud) {
        Sesion sesion = autenticacion.iniciar(peticion.codigo(), peticion.contrasena(), solicitud.getRemoteAddr());
        return TokenRespuesta.de(sesion.token(), sesion.minutos(), UsuarioSesion.de(sesion.usuario()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cierra la sesión: el token deja de valer en el servidor aunque no haya vencido (RF02)")
    public void logout(HttpServletRequest solicitud) {
        String cabecera = solicitud.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO)) {
            autenticacion.cerrar(cabecera.substring(PREFIJO.length()));
        }
    }

    /** Se releen de la base para reflejar cambios hechos por el administrador durante la sesión */
    @GetMapping("/perfil")
    @Operation(summary = "Detalle de la cuenta del usuario autenticado")
    public PerfilRespuesta perfil(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        Usuario usuario = servicioUsuario.obtener(autenticado.id());
        Long aCargo = usuario.getRol() == Rol.RESPONSABLE ? servidoresACargo.contarACargoDe(usuario.getId()) : null;
        return PerfilRespuesta.de(usuario, aCargo, parametrosSesion.minutosExpiracionToken());
    }

    @GetMapping("/yo")
    @Operation(summary = "Devuelve la identidad y el rol de la sesión vigente")
    public UsuarioSesion sesionActual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return UsuarioSesion.de(usuario);
    }
}
