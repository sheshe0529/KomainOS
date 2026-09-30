package com.komainos.planificacion.service.algoritmo;

import com.komainos.shared.exception.ReglaNegocioException;

/**
 * No existe un intervalo que cumpla ventana, conflictos y capacidad, o la
 * fecha solicitada no los cumple. Es una regla del proceso (422), no un fallo.
 */
public class PlanificacionImposibleException extends ReglaNegocioException {

    public PlanificacionImposibleException(String mensaje) {
        super(mensaje);
    }
}
