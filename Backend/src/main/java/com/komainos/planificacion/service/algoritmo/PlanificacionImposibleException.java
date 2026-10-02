package com.komainos.planificacion.service.algoritmo;

import com.komainos.shared.exception.ReglaNegocioException;

/** Ningún intervalo cumple ventana, conflictos y capacidad: es una regla del proceso (422), no un fallo */
public class PlanificacionImposibleException extends ReglaNegocioException {

    public PlanificacionImposibleException(String mensaje) {
        super(mensaje);
    }
}
