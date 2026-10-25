package com.komainos.mantenimiento.event;
import com.komainos.shared.model.Actor;
public record CicloMantenimientoCerrado(Integer idOrden,Actor actor){}
