package com.komainos.credencial.model;

/** Solo para credenciales documentales de servidores Linux (DEC-39) */
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
