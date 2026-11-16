package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.service.ServicioServidor.DatosConfiguracion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Sin frecuencias predeterminadas (DEC-60): la de revisión es obligatoria con la revisión periódica activa */
public record ConfiguracionPeticion(
        /** Si se omite, conserva la actual */
        @Min(value = 1, message = "La frecuencia de revisión debe ser de al menos 1 día")
        Integer frecuenciaRevisionDias,

        /** Si se omite, conserva la selección actual. Una configuración nueva habilita las revisiones. */
        Boolean revisionPeriodicaHabilitada,

        @NotNull(message = "La frecuencia de mantenimiento es obligatoria")
        @Min(value = 1, message = "La frecuencia de mantenimiento debe ser de al menos 1 día")
        Integer frecuenciaMantenimientoDias,

        @NotNull(message = "La modalidad de planificación es obligatoria")
        ModalidadPlanificacion modalidadPlanificacion,

        /** Nula: sin cuenta para esa finalidad, no se podrá analizar ni ejecutar hasta asignarla (RF05) */
        Integer idCuentaServicioEvaluacion,
        Integer idCuentaServicioMantenimiento,

        /** Al actualizar, la fechaActualizacion que leyó el formulario: si otro la cambió después, responde 409 */
        Instant versionEsperada) {

    public DatosConfiguracion aDatos() {
        return new DatosConfiguracion(frecuenciaRevisionDias, frecuenciaMantenimientoDias, modalidadPlanificacion,
                idCuentaServicioEvaluacion, idCuentaServicioMantenimiento, revisionPeriodicaHabilitada);
    }
}
