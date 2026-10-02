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
import com.komainos.seguridad.service.ServicioJwt;
import com.komainos.seguridad.service.ServicioUsuario;
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
    private final PuertoParametrosSesion parametrosSesion;
    private final ServicioUsuario servicioUsuario;
    private final PuertoServidoresACargo servidoresACargo;

    /** Credenciales inválidas o cuenta inactiva llegan como 401 genérico: no se distingue usuario inexistente de contraseña incorrecta (HU01) */
    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve el token de acceso")
    public TokenRespuesta login(@Valid @RequestBody LoginPeticion peticion) {
        Authentication autenticacion = gestorAutenticacion.authenticate(
                new UsernamePasswordAuthenticationToken(peticion.codigo(), peticion.contrasena()));

        UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacion.getPrincipal();
        long minutos = parametrosSesion.minutosExpiracionToken();
        String token = servicioJwt.generar(usuario.getUsername(), usuario.rol().name(), minutos);

        return TokenRespuesta.de(token, minutos, UsuarioSesion.de(usuario));
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
