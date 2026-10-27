package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.EjecutorRemoto;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.Paralelo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Paso remoto de los análisis: arma la solicitud, ejecuta el script sin transacción abierta y registra el resultado.
 * Varios servidores se analizan en paralelo, dentro del límite de conexiones simultáneas del adaptador.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EjecutorAnalisis {

    private final ServicioEvaluaciones evaluaciones;
    private final EjecutorRemoto remoto;

    public Evaluacion ejecutar(Integer idEvaluacion) {
        ResultadoEjecucion resultado;
        try {
            SolicitudEjecucion solicitud = evaluaciones.solicitud(idEvaluacion);
            resultado = remoto.ejecutar(solicitud);
        } catch (ReglaNegocioException ex) {
            // Por ejemplo, un parámetro obligatorio sin valor en la versión fijada
            resultado = ResultadoEjecucion.fallo(remoto.simulado(), TipoFallo.CONFIGURACION, ex.getMessage());
        }
        return evaluaciones.registrar(idEvaluacion, resultado);
    }

    public void ejecutarTodas(Collection<Integer> idsEvaluacion) {
        Paralelo.paraCada(idsEvaluacion, this::ejecutar)
                .forEach(ex -> log.error("No se pudo registrar un análisis", ex));
    }
}
