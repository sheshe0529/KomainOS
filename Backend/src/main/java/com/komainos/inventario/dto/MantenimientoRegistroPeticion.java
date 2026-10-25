package com.komainos.inventario.dto;

import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.service.ServicioServidor.DatosAltaMantenimiento;
import com.komainos.inventario.service.ServicioServidor.DatosConfiguracion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** RF17-RF19: configuración completa preparada antes de confirmar el alta. */
public record MantenimientoRegistroPeticion(
        @NotNull @Valid ConfiguracionPeticion configuracion,
        @NotEmpty(message = "Defina al menos un intervalo de mantenimiento")
        @Size(max = 50) List<@NotNull @Valid VentanaPeticion> ventanas) {

    public DatosAltaMantenimiento aDatos() {
        return new DatosAltaMantenimiento(new DatosConfiguracion(configuracion.frecuenciaRevisionDias(),
                configuracion.frecuenciaMantenimientoDias(), configuracion.modalidadPlanificacion(),
                configuracion.idCuentaServicio()), ventanas.stream()
                .map(v -> new IntervaloSemanal(v.diaInicio(), v.horaInicio(), v.diaFin(), v.horaFin())).toList(),
                configuracion.versionEsperada());
    }
}
