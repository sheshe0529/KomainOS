package com.komainos.ejecucion.model;

/**
 * Por qué no se completó una operación remota. RF71 exige distinguir comunicación, autenticación y permisos; el resto
 * orienta al administrador sobre dónde buscar la causa.
 */
public enum TipoFallo {

    COMUNICACION("Error de comunicación"),
    AUTENTICACION("Error de autenticación"),
    PERMISOS("Permisos insuficientes"),
    TIEMPO_EXCEDIDO("Tiempo de ejecución excedido"),
    EJECUCION("Error de ejecución"),
    CONTRATO("Salida no válida"),
    CONFIGURACION("Configuración del controlador");

    private final String etiqueta;

    TipoFallo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Texto que se guarda como error: empieza por la categoría para poder reconocerla en la bitácora */
    public String describir(String detalle) {
        return detalle == null || detalle.isBlank() ? etiqueta : etiqueta + ": " + detalle;
    }
}
