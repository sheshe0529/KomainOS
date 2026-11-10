package com.komainos.seguridad.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Revocaciones en memoria por sesión cerrada, token reemplazado y cuenta desactivada o con cambio de rol.
 */
@Component
public class RegistroRevocaciones {

    /** Retención mínima para revocaciones de sesiones y cuentas. */
    private static final Duration RETENCION_MINIMA = Duration.ofDays(1);

    private final Map<String, Instant> sesiones = new ConcurrentHashMap<>();
    private final Map<String, Reemplazo> reemplazados = new ConcurrentHashMap<>();
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

    /** El cierre cubre la mayor vigencia emitida, incluidas las renovaciones en curso. */
    public void revocarSesion(String idSesion, Instant vence) {
        purgar();
        Instant retenerHasta = reloj.instant().plusSeconds(retencionSegundos.get());
        Instant caducidad = vence.isAfter(retenerHasta) ? vence : retenerHasta;
        sesiones.merge(idSesion, caducidad, (anterior, nueva) -> anterior.isAfter(nueva) ? anterior : nueva);
    }

    /**
     * Token reemplazado por una renovación: sigue valiendo la gracia indicada, para las peticiones en curso de otras
     * pestañas.
     */
    public void reemplazarToken(String id, Instant vence, Duration gracia) {
        purgar();
        reemplazados.putIfAbsent(id, new Reemplazo(reloj.instant().plus(gracia), vence));
    }

    /** Los tokens de la cuenta emitidos hasta ahora dejan de valer; los nuevos sí valen */
    public void revocarSesionesDe(String codigoUsuario) {
        purgar();
        cuentas.put(codigoUsuario.toLowerCase(), reloj.instant());
    }

    /** emitido tiene precisión de segundos: un token del mismo segundo que la revocación también cae */
    public boolean estaRevocado(String id, String idSesion, String codigoUsuario, Instant emitido) {
        if (idSesion != null && sesiones.containsKey(idSesion)) {
            return true;
        }
        Reemplazo reemplazo = id == null ? null : reemplazados.get(id);
        if (reemplazo != null && reloj.instant().isAfter(reemplazo.hasta())) {
            return true;
        }
        Instant desde = codigoUsuario == null ? null : cuentas.get(codigoUsuario.toLowerCase());
        return desde != null && emitido != null && !emitido.isAfter(desde);
    }

    private void purgar() {
        Instant ahora = reloj.instant();
        sesiones.values().removeIf(vence -> vence.isBefore(ahora));
        reemplazados.values().removeIf(r -> r.vence().isBefore(ahora));
        Duration retencion = Duration.ofSeconds(retencionSegundos.get());
        cuentas.values().removeIf(desde -> desde.plus(retencion).isBefore(ahora));
    }

    /** Hasta cuándo vale el token reemplazado y cuándo vence por sí solo, para olvidarlo */
    private record Reemplazo(Instant hasta, Instant vence) {
    }
}
