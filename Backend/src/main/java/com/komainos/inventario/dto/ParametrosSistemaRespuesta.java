package com.komainos.inventario.dto;

import com.komainos.inventario.model.ParametrosSistema;
import com.komainos.inventario.service.ServicioAsignacionCuentas.CuentaEfectiva;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;
import java.util.Optional;

public record ParametrosSistemaRespuesta(Integer maxEjecucionesConcurrentes, Integer maxDuracionTareaMinutos,
                                         Integer maxDuracionMopMinutos, Integer minCiclosRachaEstable,
                                         Integer minutosExpiracionToken, Instant fechaActualizacion,
                                         ReferenciaSimple cuentaServicioEvaluacionPredeterminada,
                                         ReferenciaSimple cuentaServicioMantenimientoPredeterminada) {

    public static ParametrosSistemaRespuesta de(ParametrosSistema p, Optional<CuentaEfectiva> evaluacion, Optional<CuentaEfectiva> mantenimiento) {
        return new ParametrosSistemaRespuesta(p.getMaxEjecucionesConcurrentes(), p.getMaxDuracionTareaMinutos(),
                p.getMaxDuracionMopMinutos(), p.getMinCiclosRachaEstable(), p.getMinutosExpiracionToken(),
                p.getFechaActualizacion(),
                evaluacion.map(c -> new ReferenciaSimple(c.id(), c.nombre())).orElse(null),
                mantenimiento.map(c -> new ReferenciaSimple(c.id(), c.nombre())).orElse(null));
    }
}
