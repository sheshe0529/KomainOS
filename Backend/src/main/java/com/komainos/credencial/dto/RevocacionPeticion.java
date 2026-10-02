package com.komainos.credencial.dto;

import jakarta.validation.constraints.Size;

public record RevocacionPeticion(
        @Size(max = 1000, message = "El motivo admite hasta 1000 caracteres")
        String motivo) {
}
