package com.komainos.seguridad.service;

import com.komainos.seguridad.model.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Inicio y cierre de sesión (RF01, RF02, RNF01). Los intentos fallidos se frenan por cuenta y origen, y por origen,
 * antes de comparar la contraseña. El cierre revoca la sesión en el servidor.
 */
@Service
@RequiredArgsConstructor
public class ServicioAutenticacion {

    /** Lo que sigue valiendo el token reemplazado por una renovación, para las peticiones que ya estaban en curso */
    static final Duration GRACIA_RENOVACION = Duration.ofSeconds(30);

    private final AuthenticationManager gestorAutenticacion;
    private final ServicioJwt servicioJwt;
    private final PuertoParametrosSesion parametrosSesion;
    private final LimitadorIntentos limitador;

    /** Credenciales inválidas, cuenta inactiva o bloqueo llegan sin decir cuál de los datos falló (HU01) */
    public Sesion iniciar(String codigo, String contrasena, String origen) {
        String porCuenta = "login:" + codigo.trim().toLowerCase(Locale.ROOT) + "|" + origen;
        String porOrigen = "origen:" + origen;
        limitador.exigirPermitido(porCuenta, porOrigen);
        UsuarioAutenticado usuario;
        try {
            usuario = (UsuarioAutenticado) gestorAutenticacion.authenticate(
                    new UsernamePasswordAuthenticationToken(codigo, contrasena)).getPrincipal();
        } catch (AuthenticationException ex) {
            limitador.registrarFallo(porCuenta, LimitadorIntentos.FALLOS_POR_CUENTA);
            limitador.registrarFallo(porOrigen, LimitadorIntentos.FALLOS_POR_ORIGEN);
            throw ex;
        }
        limitador.registrarExito(porCuenta);
        long minutos = parametrosSesion.minutosExpiracionToken();
        return new Sesion(servicioJwt.generar(usuario.getUsername(), usuario.rol().name(), minutos), minutos, usuario);
    }

    /**
     * RF02: mientras el usuario usa el panel, su sesión se renueva con la vigencia configurada; así solo vence por
     * inactividad. El token anterior vale unos segundos más para las peticiones en curso de otras pestañas.
     */
    public Sesion renovar(UsuarioAutenticado usuario, String tokenActual) {
        long minutos = parametrosSesion.minutosExpiracionToken();
        String renovado = servicioJwt.renovar(tokenActual, usuario.getUsername(), usuario.rol().name(), minutos,
                GRACIA_RENOVACION).orElseThrow(() -> new BadCredentialsException("La sesión no está vigente"));
        return new Sesion(renovado, minutos, usuario);
    }

    public void cerrar(String token) {
        servicioJwt.revocarSesion(token);
    }

    public record Sesion(String token, long minutos, UsuarioAutenticado usuario) {
    }
}
