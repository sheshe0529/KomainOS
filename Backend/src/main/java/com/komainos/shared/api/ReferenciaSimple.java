package com.komainos.shared.api;

/**
 * Referencia minima a otra entidad dentro de una respuesta.
 *
 * <p>Evita dos extremos igual de incomodos para el panel: devolver solo el id
 * (obliga a una segunda peticion para mostrar un nombre) o anidar la entidad
 * completa (arrastra asociaciones que nadie pidio).
 */
public record ReferenciaSimple(Integer id, String nombre) {
}
