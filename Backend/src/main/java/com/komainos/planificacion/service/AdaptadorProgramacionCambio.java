package com.komainos.planificacion.service;

import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.service.PuertoProgramacionCambio;
import com.komainos.shared.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** La planificación ofrece al ciclo lo que necesita sin que el ciclo dependa de ella (DEC-27) */
@Service
@RequiredArgsConstructor
public class AdaptadorProgramacionCambio implements PuertoProgramacionCambio {

    private final ServicioPlanificacion planificacion;

    @Override
    public Orden inmediata(Integer idServidor, Integer idGrupo, boolean confirmarFueraVentana, String motivo,
                           Integer idEvaluacionOrigen, Actor actor) {
        return planificacion.inmediata(idServidor, idGrupo, confirmarFueraVentana, motivo, idEvaluacionOrigen, actor);
    }

    @Override
    public void verificarFecha(Integer idOrden, Instant inicio) {
        planificacion.verificarFecha(idOrden, inicio);
    }

    @Override
    public void reprogramarEnAutorizacion(Integer idOrden, Instant inicio, String motivo, Actor actor) {
        planificacion.reprogramarEnAutorizacion(idOrden, inicio, motivo, actor);
    }
}
