package com.komainos.planificacion.service.algoritmo;

import com.komainos.shared.model.Intervalo;

public record Reserva(Integer idServidor, Intervalo intervalo, Integer idOrden, String codigoOrden) {
}
