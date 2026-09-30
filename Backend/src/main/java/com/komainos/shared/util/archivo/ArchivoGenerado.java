package com.komainos.shared.util.archivo;

/** Archivo listo para descargar. */
public record ArchivoGenerado(String nombre, FormatoArchivo formato, byte[] contenido) {
}
