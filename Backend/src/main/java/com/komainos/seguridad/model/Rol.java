package com.komainos.seguridad.model;

/** El alcance del RESPONSABLE se aplica en el backend: la protección de rutas del panel es solo comodidad */
public enum Rol {
    ADMINISTRADOR,
    OPERADOR,
    RESPONSABLE;

    /** Spring Security espera el prefijo ROLE_ al evaluar hasRole(...) */
    public String autoridad() {
        return "ROLE_" + name();
    }
}
