package com.komainos.ejecucion.service;

import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;

/**
 * Adaptador Ansible (R2.2): ejecuta en un servidor el script de la solicitud. Hay una implementación real y una
 * simulada, elegidas con komainos.automatizacion.modo. Ninguna lanza excepciones por fallos remotos: los informa en el
 * resultado con su categoría.
 */
public interface PuertoAnsible {

    ResultadoEjecucion ejecutar(SolicitudEjecucion solicitud);

    /** Conectividad, autenticación y permisos de administrador de la cuenta de servicio (RF71) */
    ResultadoEjecucion probarConexion(SolicitudEjecucion solicitud);
}
