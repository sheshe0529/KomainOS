package com.komainos.planificacion.dto;

import com.komainos.planificacion.service.ServicioPlanificacion.ResumenPlanificacion;

import java.util.List;

public record ResumenPlanificacionRespuesta(int objetivosEvaluados, List<String> ordenesGeneradas,
                                            List<String> sinIntervalo) {

    public static ResumenPlanificacionRespuesta de(ResumenPlanificacion r) {
        return new ResumenPlanificacionRespuesta(r.evaluados(), r.ordenesGeneradas(), r.sinIntervalo());
    }
}
