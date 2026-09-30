package com.komainos.seguridad.model;

/**
 * Clases de usuario definidas en la especificacion.
 *
 * <p>El alcance de cada rol no es solo de menu: el RESPONSABLE unicamente ve y
 * opera sobre los servidores que tiene asignados, y ese filtro se aplica en el
 * backend. La proteccion de rutas del panel es comodidad de interfaz, no un
 * mecanismo de seguridad.
 */
public enum Rol {
    ADMINISTRADOR,
    OPERADOR,
    RESPONSABLE;

    /** Spring Security espera el prefijo ROLE_ al evaluar hasRole(...). */
    public String autoridad() {
        return "ROLE_" + name();
    }
}
