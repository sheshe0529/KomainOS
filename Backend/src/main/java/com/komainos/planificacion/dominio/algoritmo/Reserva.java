package com.komainos.planificacion.dominio.algoritmo;

import com.komainos.shared.dominio.Intervalo;

/**
 * Tiempo ya reservado en el cronograma para un servidor por una orden que
 * ocupa el cronograma (algoritmo, seccion 3). Es la entrada R del algoritmo.
 */
public record Reserva(Integer idServidor, Intervalo intervalo, Integer idOrden, String codigoOrden) {
}
