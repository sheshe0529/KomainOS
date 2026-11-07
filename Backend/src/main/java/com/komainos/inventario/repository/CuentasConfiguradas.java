package com.komainos.inventario.repository;

/** Solo los identificadores necesarios para contar usos sin cargar entidades de inventario. */
public record CuentasConfiguradas(Integer evaluacion, Integer mantenimiento) {
}
