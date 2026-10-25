package com.komainos.seguridad.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tokens que dejan de valer antes de vencer: el que se cierra con «Cerrar sesión» y todos los de una cuenta que se
 * desactiva o cambia de rol, para que reactivarla no reviva una sesión anterior. El modelo de datos no guarda sesiones,
 * así que vive en memoria: tras reiniciar el backend solo se pierden revocaciones de tokens que igual vencen en pocos
 * minutos (configuracion_sistema.minutos_expiracion_token).
 */
@Component
public class RegistroRevocaciones {

    /** Una revocación por cuenta se recuerda al menos esto, y más si se emitieron tokens más largos */
    private static final Duration RETENCION_MINIMA = Duration.ofDays(1);

    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
    private final Map<String, Instant> cuentas = new ConcurrentHashMap<>();
    private final AtomicLong retencionSegundos = new AtomicLong(RETENCION_MINIMA.toSeconds());
    private final Clock reloj;

    /** Reloj real, el mismo con que ServicioJwt fija la emisión y el vencimiento de cada token */
    public RegistroRevocaciones() {
        this(Clock.systemUTC());
    }

    RegistroRevocaciones(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * La vigencia de la sesión es un parámetro sin tope: la revocación de una cuenta debe sobrevivir al token más largo
     * que pueda seguir vigente
     */
    public void registrarVigencia(Duration vigencia) {
        retencionSegundos.accumulateAndGet(vigencia.toSeconds(), Math::max);
    }

    /** Se recuerda hasta que el token vence por sí solo */
    public void revocarToken(String id, Instant vence) {
        purgar();
        tokens.put(id, vence);
    }

    /** Los tokens de la cuenta emitidos hasta ahora dejan de valer; los nuevos sí valen */
    public void revocarSesionesDe(String codigoUsuario) {
        purgar();
        cuentas.put(codigoUsuario.toLowerCase(), reloj.instant());
    }

    /** emitido tiene precisión de segundos: un token del mismo segundo que la revocación también cae */
    public boolean estaRevocado(String id, String codigoUsuario, Instant emitido) {
        if (id != null && tokens.containsKey(id)) {
            return true;
        }
        Instant desde = codigoUsuario == null ? null : cuentas.get(codigoUsuario.toLowerCase());
        return desde != null && emitido != null && !emitido.isAfter(desde);
    }

    private void purgar() {
        Instant ahora = reloj.instant();
        tokens.values().removeIf(vence -> vence.isBefore(ahora));
        Duration retencion = Duration.ofSeconds(retencionSegundos.get());
        cuentas.values().removeIf(desde -> desde.plus(retencion).isBefore(ahora));
    }
}
