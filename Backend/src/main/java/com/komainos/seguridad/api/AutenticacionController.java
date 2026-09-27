package com.komainos.seguridad.api;

import com.komainos.inventario.dominio.ServicioParametrosSistema;
import com.komainos.inventario.dominio.ServicioServidor;
import com.komainos.seguridad.api.dto.LoginPeticion;
import com.komainos.seguridad.api.dto.PerfilRespuesta;
import com.komainos.seguridad.api.dto.TokenRespuesta;
import com.komainos.seguridad.api.dto.UsuarioSesion;
import com.komainos.seguridad.dominio.Rol;
import com.komainos.seguridad.dominio.ServicioUsuario;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.seguridad.dominio.UsuarioAutenticado;
import com.komainos.seguridad.jwt.ServicioJwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
public class AutenticacionController {

    private final AuthenticationManager gestorAutenticacion;
    private final ServicioJwt servicioJwt;
    private final ServicioParametrosSistema servicioParametros;
    private final ServicioUsuario servicioUsuario;
    private final ServicioServidor servicioServidor;

    /**
     * HU01: ante credenciales invalidas o cuenta inactiva (RNF01) la excepcion
     * de Spring Security llega al manejador global como 401 con mensaje
     * generico; no se distingue usuario inexistente de contrasena incorrecta.
     */
    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve el token de acceso")
    public TokenRespuesta login(@Valid @RequestBody LoginPeticion peticion) {
        Authentication autenticacion = gestorAutenticacion.authenticate(
                new UsernamePasswordAuthenticationToken(peticion.codigo(), peticion.contrasena()));

        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacion.getPrincipal();
        long minutos = servicioParametros.obtener().getMinutosExpiracionToken();
        String token = servicioJwt.generar(usuario.getUsername(), usuario.rol().name(), minutos);

        return TokenRespuesta.de(token, minutos, UsuarioSesion.de(usuario));
    }

    /**
     * "Mi cuenta": datos de la cuenta autenticada, releidos de la base para
     * reflejar cambios hechos por el administrador durante la sesion.
     */
    @GetMapping("/perfil")
    @Operation(summary = "Detalle de la cuenta del usuario autenticado")
    public PerfilRespuesta perfil(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        Usuario usuario = servicioUsuario.obtener(autenticado.id());
        Long aCargo = usuario.getRol() == Rol.RESPONSABLE ? servicioServidor.contarACargoDe(usuario.getId()) : null;
        return PerfilRespuesta.de(usuario, aCargo, servicioParametros.obtener().getMinutosExpiracionToken());
    }

    @GetMapping("/yo")
    @Operation(summary = "Devuelve la identidad y el rol de la sesión vigente")
    public UsuarioSesion sesionActual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return UsuarioSesion.de(usuario);
    }
}
