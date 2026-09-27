package com.komainos.planificacion.api.dto;

import com.komainos.planificacion.dominio.ServicioPlanificacion.ResumenPlanificacion;

import java.util.List;

/** Resultado de ejecutar la planificacion automatica a demanda. */
public record ResumenPlanificacionRespuesta(int objetivosEvaluados, List<String> ordenesGeneradas,
                                            List<String> sinIntervalo) {

    public static ResumenPlanificacionRespuesta de(ResumenPlanificacion r) {
        return new ResumenPlanificacionRespuesta(r.evaluados(), r.ordenesGeneradas(), r.sinIntervalo());
    }
}
