package com.komainos.seguridad.config;

import com.komainos.seguridad.service.ServicioJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lee el token de la cabecera Authorization y, si es valido, deja autenticada
 * la peticion.
 *
 * <p>No lleva {@code @Component} a proposito: se declara como bean en
 * {@link ConfiguracionSeguridad}. Si fuera un componente escaneado, Spring Boot
 * lo registraria ademas como filtro de servlet para todas las rutas, con lo que
 * correria dos veces por peticion; y los slices de prueba web intentarian
 * construirlo sin sus dependencias, que no pertenecen a esa capa.
 */
@RequiredArgsConstructor
public class FiltroAutenticacionJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final ServicioJwt servicioJwt;
    private final UserDetailsService detallesUsuario;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest peticion,
                                    @NonNull HttpServletResponse respuesta,
                                    @NonNull FilterChain cadena) throws ServletException, IOException {

        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        // Ya autenticada por otro mecanismo: no se sobreescribe el contexto.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        servicioJwt.codigoUsuarioSiEsValido(cabecera.substring(PREFIJO.length()))
                .ifPresent(codigo -> autenticar(codigo, peticion));

        cadena.doFilter(peticion, respuesta);
    }

    private void autenticar(String codigo, HttpServletRequest peticion) {
        try {
            UserDetails usuario = detallesUsuario.loadUserByUsername(codigo);
            // Se relee el usuario en cada peticion a proposito: si el
            // administrador lo desactiva, el token firmado sigue siendo valido
            // pero la cuenta ya no lo esta, y isEnabled() lo corta aqui (RNF01).
            if (!usuario.isEnabled()) {
                return;
            }
            var autenticacion = new UsernamePasswordAuthenticationToken(
                    usuario, null, usuario.getAuthorities());
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(peticion));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
