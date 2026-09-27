package com.komainos.shared.archivos;

/** Archivo listo para descargar. */
public record ArchivoGenerado(String nombre, FormatoArchivo formato, byte[] contenido) {
}
