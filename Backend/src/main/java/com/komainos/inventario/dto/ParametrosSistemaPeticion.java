package com.komainos.inventario.dto;

import com.komainos.inventario.service.ServicioParametrosSistema.DatosParametros;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** RF68 (y RF02, RF65). Los minimos repiten ck_configuracion_sistema_valores. */
public record ParametrosSistemaPeticion(
        @NotNull(message = "La concurrencia máxima es obligatoria")
        @Min(value = 1, message = "La concurrencia máxima debe ser de al menos 1 servidor")
        Integer maxEjecucionesConcurrentes,

        @NotNull(message = "La duración máxima por tarea es obligatoria")
        @Min(value = 1, message = "La duración máxima por tarea debe ser de al menos 1 minuto")
        Integer maxDuracionTareaMinutos,

        @NotNull(message = "La duración máxima por MOP es obligatoria")
        @Min(value = 1, message = "La duración máxima por MOP debe ser de al menos 1 minuto")
        Integer maxDuracionMopMinutos,

        @NotNull(message = "Los ciclos de racha estable son obligatorios")
        @Min(value = 1, message = "La racha estable debe ser de al menos 1 ciclo")
        Integer minCiclosRachaEstable,

        @NotNull(message = "La vigencia de la sesión es obligatoria")
        @Min(value = 1, message = "La vigencia de la sesión debe ser de al menos 1 minuto")
        Integer minutosExpiracionToken) {

    public DatosParametros aDatos() {
        return new DatosParametros(maxEjecucionesConcurrentes, maxDuracionTareaMinutos, maxDuracionMopMinutos,
                minCiclosRachaEstable, minutosExpiracionToken);
    }
}
