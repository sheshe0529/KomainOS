package com.komainos.inventario.dominio;

/** Estado de un grupo de mantenimiento ({@code enum_estado_grupo}; RF20). */
public enum EstadoGrupo {

    /** Sin configuracion de mantenimiento: no genera ordenes (RF17). */
    PENDIENTE_DE_CONFIGURACION,

    /** Con configuracion (DEC-14). */
    ACTIVO,

    /** Desactivado por el administrador (RF20). */
    INACTIVO
}
