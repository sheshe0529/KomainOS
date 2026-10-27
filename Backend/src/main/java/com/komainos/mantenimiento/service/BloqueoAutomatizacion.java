package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.repository.OrdenRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializa los cambios de estado del ciclo y de la cola con un bloqueo consultivo de PostgreSQL que se libera al
 * confirmar o deshacer la transacción. Las transacciones son cortas: ninguna conexión remota ocurre mientras se tiene
 * el bloqueo, así un análisis lento no detiene las aprobaciones ni la cola. Si además se necesita el bloqueo de la
 * planificación, se toma siempre después de este, en ese orden, para que no haya interbloqueos.
 */
@Component
@RequiredArgsConstructor
public class BloqueoAutomatizacion {

    /** «KOMAAUTO» en ASCII: distinto del bloqueo de la planificación y del catálogo */
    static final long CLAVE = 0x4B4F4D41_4155544FL;

    private final OrdenRepositorio ordenes;

    @Transactional(propagation = Propagation.MANDATORY)
    public void tomar() {
        ordenes.bloquearTransaccion(CLAVE);
    }
}
