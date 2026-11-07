package com.komainos.credencial.dto;

import com.komainos.inventario.model.FinalidadCuenta;

/** Nula: el sistema queda sin cuenta predeterminada (RF06) */
public record CuentaPredeterminadaPeticion(Integer idCuentaServicio, @jakarta.validation.constraints.NotNull FinalidadCuenta finalidad) {
}
