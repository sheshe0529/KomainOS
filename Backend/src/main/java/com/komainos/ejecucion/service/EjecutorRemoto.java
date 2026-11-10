package com.komainos.ejecucion.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Punto único por el que el ciclo usa el adaptador: limita las conexiones simultáneas entre análisis y tareas (RNF08) y
 * convierte cualquier error inesperado del adaptador en un resultado fallido, para que una ejecución nunca quede a
 * medias por una excepción. Nunca se llama dentro de una transacción: una conexión remota puede tardar minutos.
 */
@Service
@Slf4j
public class EjecutorRemoto {

    private final PuertoAnsible transporte;
    private final Semaphore conexiones;

    public EjecutorRemoto(PuertoAnsible transporte, PropiedadesAutomatizacion propiedades) {
        this.transporte = transporte;
        this.conexiones = new Semaphore(Math.max(1, propiedades.maxConexionesSimultaneas()), true);
    }

    public ResultadoEjecucion ejecutar(SolicitudEjecucion solicitud) {
        return conPermiso(solicitud, () -> transporte.ejecutar(solicitud));
    }

    public ResultadoEjecucion probarConexion(SolicitudEjecucion solicitud) {
        return conPermiso(solicitud, () -> transporte.probarConexion(solicitud));
    }

    private ResultadoEjecucion conPermiso(SolicitudEjecucion solicitud, Supplier<ResultadoEjecucion> operacion) {
        try {
            conexiones.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ResultadoEjecucion.fallo(TipoFallo.EJECUCION, "la operación se interrumpió antes de iniciar");
        }
        try {
            return operacion.get();
        } catch (RuntimeException ex) {
            log.error("Error inesperado del adaptador ({}) en {}", solicitud.proposito(), solicitud.hostname(), ex);
            return ResultadoEjecucion.fallo(TipoFallo.CONFIGURACION,
                    "el adaptador no pudo completar la operación; revise el registro del backend");
        } finally {
            conexiones.release();
        }
    }
}
