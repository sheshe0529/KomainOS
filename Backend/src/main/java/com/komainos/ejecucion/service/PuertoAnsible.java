package com.komainos.ejecucion.service;
import com.komainos.ejecucion.model.*;
public interface PuertoAnsible {
 ResultadoEjecucion ejecutar(SolicitudEjecucion solicitud);
 ResultadoEjecucion probarConexion(SolicitudEjecucion solicitud);
}
