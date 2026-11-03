package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.service.ServicioBitacora.ResultadoMantenimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bitácora de mantenimiento (RF53): resultado de cada ciclo de un vistazo")
class ServicioBitacoraTest {

    @Test
    @DisplayName("Una orden cerrada es exitosa si ejecutó tareas, sin intervención si no, y con incidencia si la hubo")
    void cerradas() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.PENDIENTE_VALIDACION, true, false))
                .isEqualTo(ResultadoMantenimiento.EXITOSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.EN_EVALUACION, false, false))
                .isEqualTo(ResultadoMantenimiento.SIN_INTERVENCION);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.INCIDENCIA, true, true))
                .isEqualTo(ResultadoMantenimiento.CON_INCIDENCIA);
    }

    @Test
    @DisplayName("Las órdenes abiertas se distinguen por lo que esperan: programación, atención o el ciclo en curso")
    void abiertas() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.PROGRAMADA, null, false, false))
                .isEqualTo(ResultadoMantenimiento.PROGRAMADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.REPROGRAMADA, null, false, false))
                .isEqualTo(ResultadoMantenimiento.PROGRAMADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.EN_EJECUCION, null, true, false))
                .isEqualTo(ResultadoMantenimiento.EN_CURSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.PENDIENTE_VALIDACION, null, true, false))
                .isEqualTo(ResultadoMantenimiento.EN_CURSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.INCIDENCIA, null, true, true))
                .isEqualTo(ResultadoMantenimiento.REQUIERE_ATENCION);
        assertThat(ServicioBitacora.resultado(EstadoOrden.SIN_IMPLEMENTACION, null, false, false))
                .isEqualTo(ResultadoMantenimiento.REQUIERE_ATENCION);
    }

    @Test
    @DisplayName("Toda orden termina en CERRADA: el estado anterior al cierre dice si se canceló o no se autorizó")
    void cerradasSinRealizar() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.CANCELADA, false, false))
                .isEqualTo(ResultadoMantenimiento.CANCELADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.RECHAZADA, false, false))
                .isEqualTo(ResultadoMantenimiento.NO_AUTORIZADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.AUTORIZACION_VENCIDA, false, false))
                .isEqualTo(ResultadoMantenimiento.NO_AUTORIZADO);
        // Cerrada sin intervenir porque faltaba una implementación: no se hizo, no es que el servidor estuviera sano
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, EstadoOrden.SIN_IMPLEMENTACION, false, false))
                .isEqualTo(ResultadoMantenimiento.CANCELADO);
    }

    @Test
    @DisplayName("Cancelar, rechazar o dejar vencer la autorización no cuentan como mantenimiento realizado")
    void sinRealizar() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.CANCELADA, null, false, false))
                .isEqualTo(ResultadoMantenimiento.CANCELADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.RECHAZADA, null, false, false))
                .isEqualTo(ResultadoMantenimiento.NO_AUTORIZADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.AUTORIZACION_VENCIDA, null, false, false))
                .isEqualTo(ResultadoMantenimiento.NO_AUTORIZADO);
    }
}
