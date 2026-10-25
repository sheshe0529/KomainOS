package com.komainos.seguridad.service;

import com.komainos.shared.exception.DemasiadosIntentosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frena los intentos repetidos de contraseña en el inicio de sesión y la reautenticación (RNF01). Cada clave (cuenta y
 * origen, origen solo o usuario que se reautentica) admite unos fallos seguidos y después se bloquea con una espera que
 * se duplica hasta un máximo. Un acierto limpia la clave.
 *
 * <p>Vive en memoria: con varias instancias del backend, o detrás de un proxy que no entregue la IP real, conviene
 * complementarlo en la infraestructura.
 */
@Slf4j
@Component
public class LimitadorIntentos {

    /** Fallos seguidos que admite una cuenta desde un mismo origen */
    static final int FALLOS_POR_CUENTA = 5;
    /** Un origen que prueba muchas cuentas distintas se frena aparte, con más margen */
    static final int FALLOS_POR_ORIGEN = 30;
    static final Duration ESPERA_INICIAL = Duration.ofMinutes(1);
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(15);
    /** Sin fallos durante este tiempo, la clave se olvida */
    private static final Duration OLVIDO = Duration.ofMinutes(30);
    private static final int MAXIMO_CLAVES = 10_000;

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimitadorIntentos(Clock reloj) {
        this.reloj = reloj;
    }

    /** Antes de comparar la contraseña: así un bloqueo no gasta CPU en BCrypt */
    public void exigirPermitido(String... claves) {
        Instant ahora = reloj.instant();
        for (String clave : claves) {
            Estado estado = estados.get(clave);
            if (estado != null && estado.bloqueadoHasta != null && ahora.isBefore(estado.bloqueadoHasta)) {
                throw new DemasiadosIntentosException(Math.max(1, Duration.between(ahora, estado.bloqueadoHasta).toSeconds()));
            }
        }
    }

    public void registrarFallo(String clave, int fallosPermitidos) {
        Instant ahora = reloj.instant();
        purgarSiCrece(ahora);
        estados.compute(clave, (k, previo) -> {
            Estado estado = previo == null || previo.olvidable(ahora) ? new Estado() : previo;
            estado.fallos++;
            estado.ultimoFallo = ahora;
            if (estado.fallos >= fallosPermitidos) {
                int excesos = estado.fallos - fallosPermitidos;
                Duration espera = ESPERA_INICIAL.multipliedBy(1L << Math.min(excesos, 10));
                estado.bloqueadoHasta = ahora.plus(espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera);
                log.warn("Clave de autenticación bloqueada hasta {} tras {} fallos: {}", estado.bloqueadoHasta,
                        estado.fallos, k.substring(0, k.indexOf(':') + 1) + "…");
            }
            return estado;
        });
    }

    public void registrarExito(String... claves) {
        for (String clave : claves) {
            estados.remove(clave);
        }
    }

    private void purgarSiCrece(Instant ahora) {
        if (estados.size() > MAXIMO_CLAVES) {
            estados.entrySet().removeIf(e -> e.getValue().olvidable(ahora));
        }
    }

    private static final class Estado {
        int fallos;
        Instant ultimoFallo;
        Instant bloqueadoHasta;

        boolean olvidable(Instant ahora) {
            boolean bloqueoVencido = bloqueadoHasta == null || !ahora.isBefore(bloqueadoHasta);
            return bloqueoVencido && ultimoFallo != null && ahora.isAfter(ultimoFallo.plus(OLVIDO));
        }
    }
}
