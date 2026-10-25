package com.komainos.seguridad.service;

import com.komainos.seguridad.config.PropiedadesJwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * La vigencia se recibe en cada emisión: un cambio del administrador aplica sin reiniciar el backend. Cada token lleva
 * un identificador propio (jti) para poder revocarlo al cerrar sesión antes de que venza.
 */
@Service
public class ServicioJwt {

    private static final String CLAIM_ROL = "rol";

    private final SecretKey llave;
    private final long expiracionPorDefectoMinutos;
    private final RegistroRevocaciones revocaciones;

    public ServicioJwt(PropiedadesJwt propiedades, RegistroRevocaciones revocaciones) {
        this.llave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(propiedades.secreto()));
        this.expiracionPorDefectoMinutos = propiedades.expiracionMinutos();
        this.revocaciones = revocaciones;
    }

    public String generar(String codigoUsuario, String rol, long expiracionMinutos) {
        Instant ahora = Instant.now();
        revocaciones.registrarVigencia(Duration.ofMinutes(expiracionMinutos));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
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

    /** Firma y vencimiento válidos y sin revocar; si no, se resuelve como ausencia y la cadena de seguridad decide el 401 */
    public Optional<TokenVerificado> verificar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(llave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            TokenVerificado verificado = new TokenVerificado(claims.getId(), claims.getSubject(),
                    instante(claims.getIssuedAt()), instante(claims.getExpiration()));
            if (verificado.codigoUsuario() == null
                    || revocaciones.estaRevocado(verificado.id(), verificado.codigoUsuario(), verificado.emitido())) {
                return Optional.empty();
            }
            return Optional.of(verificado);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<String> codigoUsuarioSiEsValido(String token) {
        return verificar(token).map(TokenVerificado::codigoUsuario);
    }

    /** Cierra la sesión de ese token: deja de valer aunque no haya vencido */
    public void revocar(TokenVerificado token) {
        if (token.id() != null && token.vence() != null) {
            revocaciones.revocarToken(token.id(), token.vence());
        }
    }

    private static Instant instante(Date fecha) {
        return fecha == null ? null : fecha.toInstant();
    }

    public record TokenVerificado(String id, String codigoUsuario, Instant emitido, Instant vence) {
    }
}
