package com.komainos.credencial.model;

/** Tipo de acceso Linux (DEC-39). En cuentas de servicio, GENERICO habilita la elevación con su. */
public enum TipoUsuario {
    /** Tiene privilegios de administrador por sí mismo */
    ADMINISTRADOR("Administrador"),
    /** Para subir a root necesita la contraseña su */
    GENERICO("Genérico");

    private final String etiqueta;

    TipoUsuario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Como se escribe en los archivos de intercambio */
    public String etiqueta() {
        return etiqueta;
    }
}
