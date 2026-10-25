package com.komainos.planificacion.service;
import com.komainos.mantenimiento.service.PuertoProgramacionCambio;import com.komainos.mantenimiento.model.Orden;import com.komainos.shared.model.Actor;import java.time.Instant;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AdaptadorProgramacionCambio implements PuertoProgramacionCambio{
 private final ServicioPlanificacion planificacion;
 public Orden inmediata(Integer s,Integer g,boolean confirmar,String motivo,Integer evaluacion,Actor actor){return planificacion.inmediata(s,g,confirmar,motivo,evaluacion,actor);}
 public void reprogramarAutorizacion(Integer id,Instant inicio,String motivo,Actor actor){planificacion.reprogramarEnAutorizacion(id,inicio,motivo,actor);}
}
