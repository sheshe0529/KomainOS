package com.komainos.seguridad.jwt;

import com.komainos.seguridad.config.PropiedadesJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * Emision y verificacion de los tokens de acceso (RF01, RF02).
 *
 * <p>La vigencia la define {@code configuracion_sistema.minutos_expiracion_token}
 * y se recibe en cada emision: asi un cambio del administrador aplica a las
 * sesiones siguientes sin reiniciar el backend. Al vencer, el token deja de
 * validar y RF02 exige autenticarse de nuevo.
 */
@Service
public class ServicioJwt {

    private static final String CLAIM_ROL = "rol";

    private final SecretKey llave;
    private final long expiracionPorDefectoMinutos;

    public ServicioJwt(PropiedadesJwt propiedades) {
        this.llave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(propiedades.secreto()));
        this.expiracionPorDefectoMinutos = propiedades.expiracionMinutos();
    }

    public String generar(String codigoUsuario, String rol, long expiracionMinutos) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(codigoUsuario)
                .claims(Map.of(CLAIM_ROL, rol))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expiracionMinutos * 60)))
                .signWith(llave)
                .compact();
    }

    public long expiracionPorDefectoMinutos() {
        return expiracionPorDefectoMinutos;
    }

    /**
     * Devuelve el codigo de usuario si el token es autentico y esta vigente.
     * Un token invalido se resuelve como ausencia: la cadena de seguridad
     * decide luego el 401.
     */
    public Optional<String> codigoUsuarioSiEsValido(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(llave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
