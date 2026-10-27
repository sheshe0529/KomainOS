package com.komainos.ejecucion.model;

/**
 * Archivo que se copia y ejecuta en el servidor: el código de la versión con el bloque de parámetros de KomainOS.
 *
 * @param lenguaje              bash o powershell, para el resaltado del panel
 * @param lineaInicioParametros primera línea del bloque de parámetros (desde 1); 0 si la versión no declara parámetros
 * @param lineaFinParametros    última línea del bloque; 0 si no hay bloque
 */
public record ScriptPreparado(String nombreArchivo, String lenguaje, String contenido,
                              int lineaInicioParametros, int lineaFinParametros) {
}
