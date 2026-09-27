package com.komainos.planificacion.dominio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proceso programado del actor Sistema que genera las ordenes de los
 * servidores y grupos en modalidad automatica (RF27, RF29, EA01).
 *
 * <p>Cubre el primer ciclo de cada objetivo y todo ciclo cerrado sin orden
 * siguiente, y reintenta los que no encontraron intervalo en la corrida
 * anterior. Se desactiva en las pruebas para que no interfiera con ellas.
 */
@Component
@ConditionalOnProperty(prefix = "komainos.planificacion.proceso-automatico", name = "habilitado",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ProcesoPlanificacionAutomatica {

    private final ServicioPlanificacion servicio;

    @Scheduled(initialDelayString = "PT20S",
            fixedDelayString = "${komainos.planificacion.proceso-automatico.intervalo:PT15M}")
    public void ejecutar() {
        try {
            servicio.planificarCiclosAutomaticos();
        } catch (RuntimeException ex) {
            // Un error inesperado no debe detener las corridas siguientes.
            log.error("Falló la corrida de planificación automática", ex);
        }
    }
}
