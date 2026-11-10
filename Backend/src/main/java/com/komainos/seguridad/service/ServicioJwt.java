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
 * Cada token lleva su identificador (jti) y el de la sesión (sid), que se conserva al renovar.
 */
@Service
public class ServicioJwt {

    private static final String CLAIM_ROL = "rol";
    private static final String CLAIM_SESION = "sid";

    private final SecretKey llave;
    private final long expiracionPorDefectoMinutos;
    private final RegistroRevocaciones revocaciones;

    public ServicioJwt(PropiedadesJwt propiedades, RegistroRevocaciones revocaciones) {
        this.llave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(propiedades.secreto()));
        this.expiracionPorDefectoMinutos = propiedades.expiracionMinutos();
        this.revocaciones = revocaciones;
    }

    public String generar(String codigoUsuario, String rol, long expiracionMinutos) {
        Duration vigencia = Duration.ofMinutes(expiracionMinutos);
        revocaciones.registrarVigencia(vigencia);
        return generar(codigoUsuario, rol, UUID.randomUUID().toString(), Instant.now(), vigencia);
    }

    public Optional<String> renovar(String token, String codigoUsuario, String rol, long expiracionMinutos, Duration gracia) {
        // Fija el vencimiento y registra la vigencia antes de verificar. Un cierre concurrente cubre también este JWT.
        Instant emitido = Instant.now();
        Duration vigencia = Duration.ofMinutes(expiracionMinutos);
        revocaciones.registrarVigencia(vigencia);
        return verificar(token).filter(t -> codigoUsuario.equals(t.codigoUsuario())).map(t -> {
            String nuevo = generar(codigoUsuario, rol, t.idSesion(), emitido, vigencia);
            revocaciones.reemplazarToken(t.id(), t.vence(), gracia);
            return nuevo;
        });
    }

    private String generar(String codigoUsuario, String rol, String idSesion, Instant emitido, Duration vigencia) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(codigoUsuario)
                .claims(Map.of(CLAIM_ROL, rol, CLAIM_SESION, idSesion))
                .issuedAt(Date.from(emitido))
                .expiration(Date.from(emitido.plus(vigencia)))
                .signWith(llave)
                .compact();
    }

    public long expiracionPorDefectoMinutos() {
        return expiracionPorDefectoMinutos;
    }

    /** Firma y vencimiento válidos y sin revocar; si no, se resuelve como ausencia y la cadena de seguridad decide el 401 */
    public Optional<TokenVerificado> verificar(String token) {
        return leerFirmado(token).filter(t -> !revocaciones.estaRevocado(
                t.id(), t.idSesion(), t.codigoUsuario(), t.emitido()));
    }

    private Optional<TokenVerificado> leerFirmado(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(llave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String id = claims.getId();
            if (id == null || claims.getSubject() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            String idSesion = claims.get(CLAIM_SESION, String.class);
            // Los JWT anteriores al cambio usan su jti como identificador de sesión.
            return Optional.of(new TokenVerificado(id, idSesion == null ? id : idSesion, claims.getSubject(),
                    instante(claims.getIssuedAt()), instante(claims.getExpiration())));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<String> codigoUsuarioSiEsValido(String token) {
        return verificar(token).map(TokenVerificado::codigoUsuario);
    }

    /** Un JWT firmado y vigente puede cerrar su sesión aunque una renovación ya lo haya reemplazado. */
    public void revocarSesion(String token) {
        leerFirmado(token).ifPresent(t -> revocaciones.revocarSesion(t.idSesion(), t.vence()));
    }

    private static Instant instante(Date fecha) {
        return fecha == null ? null : fecha.toInstant();
    }

    public record TokenVerificado(String id, String idSesion, String codigoUsuario, Instant emitido, Instant vence) {
    }
}
