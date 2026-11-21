package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.MotivoCierre;
import com.komainos.mantenimiento.service.ServicioBitacora.ResultadoMantenimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bitácora de mantenimiento (RF53): resultado de cada ciclo de un vistazo")
class ServicioBitacoraTest {

    @Test
    @DisplayName("Una orden cerrada es exitosa si ejecutó tareas, sin intervención si no, y con incidencia si la hubo")
    void cerradas() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.EXITOSO, true, false))
                .isEqualTo(ResultadoMantenimiento.EXITOSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.SIN_INTERVENCION, false, false))
                .isEqualTo(ResultadoMantenimiento.SIN_INTERVENCION);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.CON_INCIDENCIA, true, true))
                .isEqualTo(ResultadoMantenimiento.CON_INCIDENCIA);
        // En un grupo cerrado con incidencia, el servidor que no la tuvo y ejecutó sus tareas sí se mantuvo
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.CON_INCIDENCIA, true, false))
                .isEqualTo(ResultadoMantenimiento.EXITOSO);
        // La validación vencida no presume conformidad, pero lo ejecutado se hizo (RF57)
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.VALIDACION_VENCIDA, true, false))
                .isEqualTo(ResultadoMantenimiento.EXITOSO);
    }

    @Test
    @DisplayName("Las órdenes abiertas se distinguen por lo que esperan: programación, atención o el ciclo en curso")
    void abiertas() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.PROGRAMADA, null, false, false))
                .isEqualTo(ResultadoMantenimiento.PROGRAMADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.EN_EJECUCION, null, true, false))
                .isEqualTo(ResultadoMantenimiento.EN_CURSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.EN_VALIDACION_TECNICA, null, true, false))
                .isEqualTo(ResultadoMantenimiento.EN_CURSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.PENDIENTE_VALIDACION_FUNCIONAL, null, true, false))
                .isEqualTo(ResultadoMantenimiento.EN_CURSO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.INCIDENCIA, null, true, true))
                .isEqualTo(ResultadoMantenimiento.REQUIERE_ATENCION);
    }

    @Test
    @DisplayName("El motivo de cierre dice si se canceló o no se autorizó (DEC-61)")
    void cerradasSinRealizar() {
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.CANCELADA, false, false))
                .isEqualTo(ResultadoMantenimiento.CANCELADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.RECHAZADA, false, false))
                .isEqualTo(ResultadoMantenimiento.NO_AUTORIZADO);
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.AUTORIZACION_VENCIDA, false, false))
                .isEqualTo(ResultadoMantenimiento.NO_AUTORIZADO);
        // Cerrada sin intervenir porque faltaba una implementación: no se hizo, no es que el servidor estuviera sano
        assertThat(ServicioBitacora.resultado(EstadoOrden.CERRADA, MotivoCierre.SIN_IMPLEMENTACION, false, false))
                .isEqualTo(ResultadoMantenimiento.CANCELADO);
    }
}
