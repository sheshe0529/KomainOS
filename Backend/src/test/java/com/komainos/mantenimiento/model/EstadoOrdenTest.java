package com.komainos.mantenimiento.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Máquina de estados de la orden (R2.1, tabla 6)")
class EstadoOrdenTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PROGRAMADA, EN_EVALUACION", "PROGRAMADA, REPROGRAMADA", "PROGRAMADA, CANCELADA",
            "REPROGRAMADA, PROGRAMADA", "AUTORIZADA, CANCELADA", "EN_COLA, REPROGRAMADA",
            "CANCELADA, CERRADA", "INCIDENCIA, CERRADA"})
    @DisplayName("transiciones permitidas")
    void permitidas(EstadoOrden origen, EstadoOrden destino) {
        assertThat(origen.puedePasarA(destino)).isTrue();
    }

    @ParameterizedTest(name = "{0} -/-> {1}")
    @CsvSource({
            "PROGRAMADA, EN_EJECUCION", "PROGRAMADA, CERRADA", "EN_EVALUACION, CANCELADA",
            "PENDIENTE_AUTORIZACION, CANCELADA", "CERRADA, PROGRAMADA", "REPROGRAMADA, CANCELADA"})
    @DisplayName("transiciones no permitidas")
    void noPermitidas(EstadoOrden origen, EstadoOrden destino) {
        assertThat(origen.puedePasarA(destino)).isFalse();
    }

    @Test
    @DisplayName("solo las órdenes que aún pueden intervenir ocupan el cronograma")
    void ocupacion() {
        assertThat(EstadoOrden.PROGRAMADA.ocupaCronograma()).isTrue();
        assertThat(EstadoOrden.EN_EJECUCION.ocupaCronograma()).isTrue();
        assertThat(EstadoOrden.CANCELADA.ocupaCronograma()).isFalse();
        assertThat(EstadoOrden.PENDIENTE_VALIDACION.ocupaCronograma()).isFalse();
        assertThat(EstadoOrden.CERRADA.ocupaCronograma()).isFalse();
    }
}
