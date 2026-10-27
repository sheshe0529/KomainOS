package com.komainos.catalogo.dto;

import com.komainos.catalogo.model.NivelAfectacion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Prioridad y duración son objetos para detectar su ausencia en vez de tomar cero (CP071) */
public record TareaPeticion(
        @NotNull(message = "Seleccione el script de mantenimiento de la tarea")
        Integer idScript,

        @NotBlank(message = "El nombre de la tarea es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        @NotNull(message = "Indique la afectación de la tarea")
        NivelAfectacion nivelAfectacion,

        @NotNull(message = "Indique la prioridad de la tarea")
        @Min(value = 0, message = "La prioridad no puede ser negativa")
        Integer prioridad,

        @NotNull(message = "Indique la duración estimada de la tarea")
        @Min(value = 1, message = "La duración estimada es de al menos un minuto")
        Integer duracionEstimadaMinutos,

        boolean activo,

        @NotNull(message = "Indique los umbrales de la tarea, aunque la lista esté vacía")
        @Size(max = 1000, message = "Una tarea admite hasta 1000 umbrales")
        Set<@NotNull Integer> idsUmbrales) {
}
