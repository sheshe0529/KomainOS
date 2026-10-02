package com.komainos.planificacion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Genera las órdenes automáticas y reintenta las que no encontraron intervalo (RF27, RF29) */
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
            // Un error inesperado no debe detener las corridas siguientes
            log.error("Falló la corrida de planificación automática", ex);
        }
    }
}
