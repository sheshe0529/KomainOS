package com.komainos.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RegistroServidorPeticion(@NotNull @Valid ServidorPeticion servidor,
                                      @NotNull @Valid MantenimientoRegistroPeticion mantenimiento) {
}
