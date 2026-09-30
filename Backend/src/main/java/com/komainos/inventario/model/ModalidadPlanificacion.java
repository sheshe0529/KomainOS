package com.komainos.inventario.model;

/**
 * Modalidad de planificacion (RF70, {@code enum_modalidad_planificacion}).
 * En AUTOMATICA el sistema genera el siguiente ciclo; en BAJO_DEMANDA las
 * ordenes nacen de una solicitud del administrador.
 */
public enum ModalidadPlanificacion {
    AUTOMATICA,
    BAJO_DEMANDA
}
