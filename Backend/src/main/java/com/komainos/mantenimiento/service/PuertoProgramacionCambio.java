package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.Orden;import com.komainos.shared.model.Actor;import java.time.Instant;
public interface PuertoProgramacionCambio {
 Orden inmediata(Integer servidor,Integer grupo,boolean confirmarFueraVentana,String motivo,Integer evaluacionOrigen,Actor actor);
 void reprogramarAutorizacion(Integer orden,Instant inicio,String motivo,Actor actor);
}
