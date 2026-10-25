package com.komainos.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record PreparacionImportacionPeticion(@Positive int fila, Integer idServidorExistente,
                                            Instant versionServidor,
                                            @NotNull @Valid MantenimientoRegistroPeticion mantenimiento) {
}
