package com.komainos.catalogo.dto;

import java.util.List;

/**
 * Archivo que se ejecutará en el servidor (vista previa): el código con el bloque de parámetros de KomainOS.
 *
 * @param problemas lo que impediría publicar el borrador; vacío en una versión ya publicada
 */
public record ScriptPreparadoRespuesta(String nombreArchivo, String lenguaje, String contenido,
                                       int lineaInicioParametros, int lineaFinParametros, List<String> problemas) {
}
