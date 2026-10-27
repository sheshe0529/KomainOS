package com.komainos.mantenimiento.dto;

import com.komainos.mantenimiento.model.EstadoAutorizacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Peticiones del ciclo de mantenimiento, con los mensajes de validación en español (RNF05) */
public final class CicloPeticiones {

    private CicloPeticiones() {
    }

    /** RF47, RF48: el motivo es obligatorio en cualquier decisión (CP119) */
    public record DecidirAutorizacionPeticion(
            @NotNull(message = "Indique si aprueba, rechaza o propone otra fecha")
            EstadoAutorizacion decision,

            @NotBlank(message = "Registre el motivo de la decisión")
            @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
            String motivo,

            /** Solo al proponer otra fecha */
            Instant fechaPropuesta) {
    }

    /** RF57: conformidad o rechazo del responsable con sus observaciones */
    public record ValidarOrdenPeticion(
            boolean conforme,

            @NotBlank(message = "Registre sus observaciones")
            @Size(max = 2000, message = "Las observaciones no pueden superar los 2000 caracteres")
            String observaciones) {
    }

    public record MotivoPeticion(
            @NotBlank(message = "Registre el motivo")
            @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
            String motivo) {
    }

    /** RF31: servidor o grupo, motivo y, si el inicio queda fuera de la ventana, la confirmación expresa */
    public record LanzamientoInmediatoPeticion(
            Integer idServidor,

            Integer idGrupo,

            boolean confirmarFueraVentana,

            @NotBlank(message = "Registre el motivo del lanzamiento")
            @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres")
            String motivo) {
    }

    /** RF61: para cerrar, el servicio exige diagnóstico y resultado (CP150) */
    public record AtenderIncidenciaPeticion(
            @NotBlank(message = "Registre el diagnóstico")
            @Size(max = 10000, message = "El diagnóstico no puede superar los 10000 caracteres")
            String diagnostico,

            @Size(max = 10000, message = "Las acciones no pueden superar los 10000 caracteres")
            String acciones,

            @NotBlank(message = "Registre el resultado de la atención")
            @Size(max = 1000, message = "El resultado no puede superar los 1000 caracteres")
            String resultado,

            boolean cerrar) {
    }

    public record ProbarAnalisisPeticion(
            @NotNull(message = "Seleccione el servidor en el que se prueba el análisis")
            Integer idServidor) {
    }
}
