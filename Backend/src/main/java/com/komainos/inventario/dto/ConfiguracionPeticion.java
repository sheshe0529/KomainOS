package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.service.ServicioServidor.DatosConfiguracion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Las frecuencias omitidas se copian del nivel de criticidad (HU11 CA2) */
public record ConfiguracionPeticion(
        @Min(value = 1, message = "La frecuencia de revisión debe ser de al menos 1 día")
        Integer frecuenciaRevisionDias,

        /** Si se omite, conserva la selección actual. Una configuración nueva habilita las revisiones. */
        Boolean revisionPeriodicaHabilitada,

        @Min(value = 1, message = "La frecuencia de mantenimiento debe ser de al menos 1 día")
        Integer frecuenciaMantenimientoDias,

        @NotNull(message = "La modalidad de planificación es obligatoria")
        ModalidadPlanificacion modalidadPlanificacion,

        /** Nula: usa la cuenta predeterminada del sistema (RF05, RF06) */
        Integer idCuentaServicioEvaluacion,
        Integer idCuentaServicioMantenimiento,

        /** Al actualizar, la fechaActualizacion que leyó el formulario: si otro la cambió después, responde 409 */
        Instant versionEsperada) {

    public DatosConfiguracion aDatos() {
        return new DatosConfiguracion(frecuenciaRevisionDias, frecuenciaMantenimientoDias, modalidadPlanificacion,
                idCuentaServicioEvaluacion, idCuentaServicioMantenimiento, revisionPeriodicaHabilitada);
    }
}
