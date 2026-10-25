package com.komainos.seguridad.service;

import com.komainos.seguridad.model.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Inicio y cierre de sesión (RF01, RF02, RNF01). Los intentos fallidos se frenan por cuenta y origen, y por origen,
 * antes de comparar la contraseña; el cierre revoca el token en el servidor.
 */
@Service
@RequiredArgsConstructor
public class ServicioAutenticacion {

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

    /** El token deja de valer aunque no haya vencido; uno ya inválido no tiene nada que revocar */
    public void cerrar(String token) {
        servicioJwt.verificar(token).ifPresent(servicioJwt::revocar);
    }

    public record Sesion(String token, long minutos, UsuarioAutenticado usuario) {
    }
}
