package com.komainos.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PreparacionesImportacionPeticion(
        @NotNull @Size(max = 10000) List<@NotNull @Valid PreparacionImportacionPeticion> servidores) {
}
