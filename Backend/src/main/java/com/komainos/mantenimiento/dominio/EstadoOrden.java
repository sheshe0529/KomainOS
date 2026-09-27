package com.komainos.mantenimiento.dominio;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del ciclo de una orden de mantenimiento (R2.1, tabla 6;
 * {@code enum_estado_orden}).
 *
 * <p>Las transiciones permitidas estan escritas una sola vez aqui. La entidad
 * {@link Orden} las consulta en cada cambio, de modo que ningun punto del
 * codigo puede saltarse la maquina de estados.
 */
public enum EstadoOrden {
    PROGRAMADA,
    EN_EVALUACION,
    SIN_IMPLEMENTACION,
    PENDIENTE_AUTORIZACION,
    AUTORIZADA,
    EN_COLA,
    EN_EJECUCION,
    PENDIENTE_VALIDACION,
    INCIDENCIA,
    REPROGRAMADA,
    RECHAZADA,
    AUTORIZACION_VENCIDA,
    VALIDACION_VENCIDA,
    CANCELADA,
    CERRADA;

    private static final Map<EstadoOrden, Set<EstadoOrden>> TRANSICIONES = Map.ofEntries(
            Map.entry(PROGRAMADA, EnumSet.of(EN_EVALUACION, REPROGRAMADA, CANCELADA)),
            Map.entry(EN_EVALUACION, EnumSet.of(PENDIENTE_AUTORIZACION, AUTORIZADA, SIN_IMPLEMENTACION, CERRADA)),
            Map.entry(SIN_IMPLEMENTACION, EnumSet.of(CERRADA)),
            Map.entry(PENDIENTE_AUTORIZACION, EnumSet.of(AUTORIZADA, RECHAZADA, AUTORIZACION_VENCIDA)),
            Map.entry(AUTORIZADA, EnumSet.of(EN_COLA, CANCELADA, RECHAZADA)),
            Map.entry(EN_COLA, EnumSet.of(EN_EJECUCION, CANCELADA, REPROGRAMADA)),
            Map.entry(EN_EJECUCION, EnumSet.of(PENDIENTE_VALIDACION, INCIDENCIA)),
            Map.entry(PENDIENTE_VALIDACION, EnumSet.of(CERRADA, INCIDENCIA, VALIDACION_VENCIDA)),
            Map.entry(INCIDENCIA, EnumSet.of(CERRADA)),
            Map.entry(REPROGRAMADA, EnumSet.of(PROGRAMADA)),
            Map.entry(RECHAZADA, EnumSet.of(CERRADA)),
            Map.entry(AUTORIZACION_VENCIDA, EnumSet.of(CERRADA)),
            Map.entry(VALIDACION_VENCIDA, EnumSet.of(CERRADA)),
            Map.entry(CANCELADA, EnumSet.of(CERRADA)),
            Map.entry(CERRADA, EnumSet.noneOf(EstadoOrden.class)));

    /**
     * Estados en los que la intervencion todavia puede ocurrir o esta
     * ocurriendo: reservan tiempo en el cronograma (algoritmo, seccion 3).
     */
    private static final Set<EstadoOrden> OCUPAN_CRONOGRAMA = EnumSet.of(
            PROGRAMADA, REPROGRAMADA, EN_EVALUACION, PENDIENTE_AUTORIZACION, AUTORIZADA, EN_COLA, EN_EJECUCION);

    /**
     * El ciclo ya empezo (evaluacion en adelante) y aun no se cierra: un
     * servidor en esta situacion no se da de baja hasta que termine (RF72, DEC-25).
     */
    private static final Set<EstadoOrden> EN_CURSO = EnumSet.of(
            EN_EVALUACION, PENDIENTE_AUTORIZACION, EN_EJECUCION, PENDIENTE_VALIDACION, INCIDENCIA);

    public boolean puedePasarA(EstadoOrden destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public boolean ocupaCronograma() {
        return OCUPAN_CRONOGRAMA.contains(this);
    }

    public boolean estaEnCurso() {
        return EN_CURSO.contains(this);
    }

    public static Set<EstadoOrden> queOcupanCronograma() {
        return EnumSet.copyOf(OCUPAN_CRONOGRAMA);
    }

    public static Set<EstadoOrden> enCurso() {
        return EnumSet.copyOf(EN_CURSO);
    }

    /** Etapa del ciclo a la que pertenece el estado (RF34), atributo derivado. */
    public EtapaOrden etapa() {
        return switch (this) {
            case PROGRAMADA, REPROGRAMADA -> EtapaOrden.PLANIFICACION;
            case EN_EVALUACION, SIN_IMPLEMENTACION -> EtapaOrden.EVALUACION;
            case PENDIENTE_AUTORIZACION, AUTORIZADA, RECHAZADA, AUTORIZACION_VENCIDA -> EtapaOrden.AUTORIZACION;
            case EN_COLA, EN_EJECUCION -> EtapaOrden.EJECUCION;
            case PENDIENTE_VALIDACION, VALIDACION_VENCIDA -> EtapaOrden.VALIDACION;
            case INCIDENCIA -> EtapaOrden.INCIDENCIA;
            case CANCELADA, CERRADA -> EtapaOrden.CIERRE;
        };
    }
}
