package com.komainos.inventario.service;

import com.komainos.seguridad.model.Usuario;

import java.time.Instant;

/**
 * Lo que el inventario necesita saber y hacer sobre las ordenes de un servidor
 * para tramitar su baja (RF72), sin depender del modulo de mantenimiento.
 *
 * <p>La implementacion vive en {@code mantenimiento}; asi las dependencias
 * siguen apuntando del ciclo de mantenimiento hacia el inventario y no al reves.
 */
public interface PuertoMantenimientos {

    /**
     * Verdadero si el servidor participa en una orden cuyo ciclo ya empezo y
     * aun no termina (DEC-25). En ese caso la baja queda pendiente.
     */
    boolean tieneMantenimientoEnCurso(Integer idServidor);

    /**
     * Retira los mantenimientos pendientes del servidor: cancela sus ordenes
     * individuales y lo retira de las ordenes grupales sin afectar a los demas
     * integrantes. Devuelve la cantidad de ordenes afectadas.
     */
    int retirarPendientes(Integer idServidor, Usuario autor, String motivo, Instant ahora);
}
