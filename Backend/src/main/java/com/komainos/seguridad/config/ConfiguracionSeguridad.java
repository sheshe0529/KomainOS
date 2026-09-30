package com.komainos.seguridad.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.seguridad.service.ServicioJwt;
import com.komainos.shared.dto.ErrorRespuesta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({PropiedadesJwt.class, PropiedadesCors.class, PropiedadesAdministradorInicial.class})
@RequiredArgsConstructor
public class ConfiguracionSeguridad {

    private final PropiedadesCors propiedadesCors;
    private final ObjectMapper mapeadorJson;

    @Bean
    public FiltroAutenticacionJwt filtroAutenticacionJwt(ServicioJwt servicioJwt,
                                                         UserDetailsService detallesUsuario) {
        return new FiltroAutenticacionJwt(servicioJwt, detallesUsuario);
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http,
                                             FiltroAutenticacionJwt filtroJwt) throws Exception {
        return http
                // Sin CSRF porque no hay sesion ni cookie de autenticacion: el
                // token viaja en la cabecera Authorization.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(fuenteCors()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> req
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Todo lo demas cerrado por defecto; el permiso fino se
                        // declara con @PreAuthorize en cada controlador.
                        .anyRequest().authenticated())
                // Sin token o con token vencido: 401 con la forma comun de
                // error, para que el panel vuelva al inicio de sesion (RF02).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> escribirError(req, res, HttpStatus.UNAUTHORIZED,
                                "NO_AUTENTICADO", "Sesión no iniciada o expirada. Inicie sesión nuevamente"))
                        .accessDeniedHandler((req, res, e) -> escribirError(req, res, HttpStatus.FORBIDDEN,
                                "ACCESO_DENEGADO", "No tiene permisos para realizar esta operación")))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void escribirError(HttpServletRequest req, HttpServletResponse res, HttpStatus estado,
                               String codigo, String mensaje) throws IOException {
        res.setStatus(estado.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapeadorJson.writeValue(res.getOutputStream(),
                ErrorRespuesta.de(estado.value(), codigo, mensaje, req.getRequestURI()));
    }

    @Bean
    public CorsConfigurationSource fuenteCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(propiedadesCors.origenes());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }

    @Bean
    public UserDetailsService detallesUsuario(UsuarioRepositorio repositorio) {
        return codigo -> repositorio.findByCodigo(codigo)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }

    @Bean
    public PasswordEncoder codificadorContrasena() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager gestorAutenticacion(UserDetailsService detallesUsuario,
                                                     PasswordEncoder codificador) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider();
        proveedor.setUserDetailsService(detallesUsuario);
        proveedor.setPasswordEncoder(codificador);
        // Sin esto, un codigo inexistente responde distinto que una
        // contrasena incorrecta y permite enumerar usuarios (HU01 CA2).
        proveedor.setHideUserNotFoundExceptions(true);
        return new ProviderManager(proveedor);
    }
}
