package com.komainos.inventario.dto;

import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RF15 / HU11: frecuencias recomendadas en dias y plazos independientes de
 * autorizacion y validacion en horas. Los minimos repiten ck_nivel_criticidad_valores.
 */
public record NivelCriticidadPeticion(
        @NotBlank(message = "El nombre del nivel es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @NotNull(message = "La prioridad es obligatoria")
        @Min(value = 0, message = "La prioridad no puede ser negativa")
        Integer prioridad,

        @NotNull(message = "La frecuencia de revisión es obligatoria")
        @Min(value = 1, message = "La frecuencia de revisión debe ser de al menos 1 día")
        Integer frecuenciaRevisionDias,

        @NotNull(message = "La frecuencia de mantenimiento es obligatoria")
        @Min(value = 1, message = "La frecuencia de mantenimiento debe ser de al menos 1 día")
        Integer frecuenciaMantenimientoDias,

        @NotNull(message = "El plazo de autorización es obligatorio")
        @Min(value = 1, message = "El plazo de autorización debe ser de al menos 1 hora")
        Integer plazoAutorizacionHoras,

        @NotNull(message = "El plazo de validación es obligatorio")
        @Min(value = 1, message = "El plazo de validación debe ser de al menos 1 hora")
        Integer plazoValidacionHoras) {

    public DatosNivelCriticidad aDatos() {
        return new DatosNivelCriticidad(nombre, prioridad, frecuenciaRevisionDias, frecuenciaMantenimientoDias,
                plazoAutorizacionHoras, plazoValidacionHoras);
    }
}
