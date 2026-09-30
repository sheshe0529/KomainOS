package com.komainos.inventario.model;

/**
 * Estado administrativo del servidor ({@code enum_estado_servidor}; HU07 CA5).
 *
 * <p>Los valores son los de la base vigente: {@code DADO_DE_BAJA} y no
 * {@code BAJA} como indica la tabla 50 de R2.4 (DEC-03).
 */
public enum EstadoServidor {

    /** Datos basicos registrados sin configuracion de mantenimiento: no genera ordenes (HU06 CA3). */
    PENDIENTE_DE_CONFIGURACION,

    /** Tiene configuracion de mantenimiento (DEC-14). */
    ACTIVO,

    /** Retirado: conserva su historial y no admite mantenimientos nuevos (RF72). */
    DADO_DE_BAJA
}
