package com.komainos.mantenimiento.dto;

import com.komainos.catalogo.dto.RevisionSalidaRespuesta;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.PrioridadOrden;

import java.time.Instant;
import java.util.List;

/** Respuestas de la sala de operaciones, la cola, el historial de ejecuciones y las pruebas de acceso */
public final class OperacionRespuestas {

    private OperacionRespuestas() {
    }

    /** Cola de ejecución por servidor (RF35, HU22) */
    public record ColaRespuesta(Integer idDetalle, Integer idOrden, String orden, Integer idServidor, String servidor,
                                PrioridadOrden prioridad, EstadoDetalleOrden estado, int posicion, boolean piloto,
                                Instant inicioPrevisto, Instant finPrevisto, Instant inicioReal) {
    }

    /** RF26: tareas ejecutadas o fijadas con una versión */
    public record UsoVersionRespuesta(Integer idTareaMop, Integer idOrden, String orden, String servidor, String tarea,
                                      EstadoTareaMop estado, Instant inicio, Instant fin) {
    }

    /**
     * Historial de ejecución de scripts (R3.4): qué se ejecutó, dónde, con qué versión y cuenta, cuándo, cuánto tardó y
     * cómo terminó. La salida completa solo va en el detalle.
     */
    public record EjecucionRespuesta(Integer id, Integer idOrden, String orden, Integer idServidor, String servidor,
                                     String tarea, Integer idScript, String script, Integer idVersion,
                                     Integer numeroVersion, String sistemaOperativo, String cuenta,
                                     EstadoTareaMop estado, Integer codigoRetorno, Instant inicio, Instant fin,
                                     Long duracionSegundos, String error) {
    }

    public record EjecucionDetalleRespuesta(EjecucionRespuesta ejecucion, String salida, int prioridad,
                                            int duracionEstimadaMinutos) {
    }

    /** RF71: el error distingue comunicación, autenticación y permisos */
    public record ConexionRespuesta(Integer idServidor, String servidor, Integer idCuenta, String cuenta,
                                    boolean simulado, boolean correcto, TipoFallo fallo, String mensaje) {
    }

    /** Prueba de una versión de análisis sobre un servidor, con la revisión de su salida */
    public record PruebaAnalisisRespuesta(boolean simulado, boolean correcto, TipoFallo fallo, String error,
                                          String salida, RevisionSalidaRespuesta revision, String advertencia) {
    }

    public record ResumenProcesoRespuesta(boolean ejecutado, int evaluadas, int revisadas, int despachadas,
                                          int vencidas, List<String> mensajes) {
    }

    public record DespachoRespuesta(int despachados, String mensaje) {
    }
}
