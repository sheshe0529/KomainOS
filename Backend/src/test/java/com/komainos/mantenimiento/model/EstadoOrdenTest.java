package com.komainos.mantenimiento.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Máquina de estados de la orden (R2.1, tabla 6; DEC-61)")
class EstadoOrdenTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PROGRAMADA, EN_EVALUACION", "PROGRAMADA, PROGRAMADA", "PROGRAMADA, CERRADA",
            "EN_EVALUACION, CERRADA", "AUTORIZADA, CERRADA", "EN_COLA, PROGRAMADA",
            "EN_EJECUCION, EN_VALIDACION_TECNICA", "EN_VALIDACION_TECNICA, PENDIENTE_VALIDACION_FUNCIONAL",
            "EN_VALIDACION_TECNICA, INCIDENCIA", "PENDIENTE_VALIDACION_FUNCIONAL, INCIDENCIA", "INCIDENCIA, CERRADA"})
    @DisplayName("transiciones permitidas")
    void permitidas(EstadoOrden origen, EstadoOrden destino) {
        assertThat(origen.puedePasarA(destino)).isTrue();
    }

    @ParameterizedTest(name = "{0} -/-> {1}")
    @CsvSource({
            "PROGRAMADA, EN_EJECUCION", "EN_EJECUCION, PENDIENTE_VALIDACION_FUNCIONAL", "EN_EJECUCION, CERRADA",
            "EN_VALIDACION_TECNICA, CERRADA", "CERRADA, PROGRAMADA", "INCIDENCIA, PROGRAMADA"})
    @DisplayName("transiciones no permitidas")
    void noPermitidas(EstadoOrden origen, EstadoOrden destino) {
        assertThat(origen.puedePasarA(destino)).isFalse();
    }

    @Test
    @DisplayName("solo las órdenes que aún pueden intervenir ocupan el cronograma; el análisis posterior también")
    void ocupacion() {
        assertThat(EstadoOrden.queOcupanCronograma())
                .contains(EstadoOrden.PROGRAMADA, EstadoOrden.EN_EJECUCION, EstadoOrden.EN_VALIDACION_TECNICA)
                .doesNotContain(EstadoOrden.PENDIENTE_VALIDACION_FUNCIONAL, EstadoOrden.INCIDENCIA, EstadoOrden.CERRADA);
    }

    @Test
    @DisplayName("la validación técnica y la funcional pertenecen a la etapa de validación")
    void etapas() {
        assertThat(EstadoOrden.EN_VALIDACION_TECNICA.etapa()).isEqualTo(EtapaOrden.VALIDACION);
        assertThat(EstadoOrden.PENDIENTE_VALIDACION_FUNCIONAL.etapa()).isEqualTo(EtapaOrden.VALIDACION);
        assertThat(EstadoOrden.CERRADA.etapa()).isEqualTo(EtapaOrden.CIERRE);
    }

    @ParameterizedTest(name = "{0} desde {1}")
    @CsvSource({
            "EXITOSO, PENDIENTE_VALIDACION_FUNCIONAL", "SIN_INTERVENCION, EN_EVALUACION",
            "SIN_IMPLEMENTACION, EN_EVALUACION", "AUTORIZACION_VENCIDA, PENDIENTE_AUTORIZACION",
            "VALIDACION_VENCIDA, PENDIENTE_VALIDACION_FUNCIONAL", "CANCELADA, PROGRAMADA", "CANCELADA, EN_COLA",
            "RECHAZADA, PENDIENTE_AUTORIZACION", "CON_INCIDENCIA, INCIDENCIA"})
    @DisplayName("cada motivo de cierre se admite desde los estados en que ocurre")
    void motivosAdmitidos(MotivoCierre motivo, EstadoOrden estado) {
        assertThat(motivo.admiteCierreDesde(estado)).isTrue();
        assertThat(estado.puedePasarA(EstadoOrden.CERRADA)).isTrue();
    }

    @ParameterizedTest(name = "{0} no desde {1}")
    @CsvSource({
            "EXITOSO, INCIDENCIA", "CANCELADA, EN_EJECUCION", "CON_INCIDENCIA, PENDIENTE_VALIDACION_FUNCIONAL",
            "SIN_INTERVENCION, PROGRAMADA", "VALIDACION_VENCIDA, EN_VALIDACION_TECNICA"})
    @DisplayName("un motivo no se admite desde un estado en que no puede ocurrir")
    void motivosNoAdmitidos(MotivoCierre motivo, EstadoOrden estado) {
        assertThat(motivo.admiteCierreDesde(estado)).isFalse();
    }
}
