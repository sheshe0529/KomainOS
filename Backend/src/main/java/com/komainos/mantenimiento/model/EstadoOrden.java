package com.komainos.mantenimiento.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Transiciones definidas una sola vez: Orden las valida en cada cambio (R2.1, tabla 6; DEC-61). El estado dice dónde
 * está la orden; cómo terminó lo dice su {@link MotivoCierre}. La reprogramación deja la orden en PROGRAMADA y queda en
 * el historial y la auditoría.
 */
public enum EstadoOrden {
    PROGRAMADA,
    EN_EVALUACION,
    PENDIENTE_AUTORIZACION,
    AUTORIZADA,
    EN_COLA,
    EN_EJECUCION,
    EN_VALIDACION_TECNICA,
    PENDIENTE_VALIDACION_FUNCIONAL,
    INCIDENCIA,
    CERRADA;

    private static final Map<EstadoOrden, Set<EstadoOrden>> TRANSICIONES = Map.ofEntries(
            Map.entry(PROGRAMADA, EnumSet.of(EN_EVALUACION, PROGRAMADA, CERRADA)),
            Map.entry(EN_EVALUACION, EnumSet.of(PENDIENTE_AUTORIZACION, AUTORIZADA, CERRADA)),
            Map.entry(PENDIENTE_AUTORIZACION, EnumSet.of(AUTORIZADA, CERRADA)),
            Map.entry(AUTORIZADA, EnumSet.of(EN_COLA, CERRADA)),
            Map.entry(EN_COLA, EnumSet.of(EN_EJECUCION, PROGRAMADA, CERRADA)),
            Map.entry(EN_EJECUCION, EnumSet.of(EN_VALIDACION_TECNICA, INCIDENCIA)),
            Map.entry(EN_VALIDACION_TECNICA, EnumSet.of(PENDIENTE_VALIDACION_FUNCIONAL, INCIDENCIA)),
            Map.entry(PENDIENTE_VALIDACION_FUNCIONAL, EnumSet.of(CERRADA, INCIDENCIA)),
            Map.entry(INCIDENCIA, EnumSet.of(CERRADA)),
            Map.entry(CERRADA, EnumSet.noneOf(EstadoOrden.class)));

    /** Estados que reservan tiempo en el cronograma; el análisis posterior corre en la ventana de la ejecución */
    private static final Set<EstadoOrden> OCUPAN_CRONOGRAMA = EnumSet.of(
            PROGRAMADA, EN_EVALUACION, PENDIENTE_AUTORIZACION, AUTORIZADA, EN_COLA, EN_EJECUCION, EN_VALIDACION_TECNICA);

    /** El ciclo ya empezó y no se cerró: el servidor no se da de baja hasta que termine (DEC-25) */
    private static final Set<EstadoOrden> EN_CURSO = EnumSet.of(
            EN_EVALUACION, PENDIENTE_AUTORIZACION, EN_EJECUCION, EN_VALIDACION_TECNICA, PENDIENTE_VALIDACION_FUNCIONAL,
            INCIDENCIA);

    public boolean puedePasarA(EstadoOrden destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public static Set<EstadoOrden> queOcupanCronograma() {
        return EnumSet.copyOf(OCUPAN_CRONOGRAMA);
    }

    public static Set<EstadoOrden> enCurso() {
        return EnumSet.copyOf(EN_CURSO);
    }

    public EtapaOrden etapa() {
        return switch (this) {
            case PROGRAMADA -> EtapaOrden.PLANIFICACION;
            case EN_EVALUACION -> EtapaOrden.EVALUACION;
            case PENDIENTE_AUTORIZACION, AUTORIZADA -> EtapaOrden.AUTORIZACION;
            case EN_COLA, EN_EJECUCION -> EtapaOrden.EJECUCION;
            case EN_VALIDACION_TECNICA, PENDIENTE_VALIDACION_FUNCIONAL -> EtapaOrden.VALIDACION;
            case INCIDENCIA -> EtapaOrden.INCIDENCIA;
            case CERRADA -> EtapaOrden.CIERRE;
        };
    }
}
