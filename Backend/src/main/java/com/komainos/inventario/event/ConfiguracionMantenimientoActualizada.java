package com.komainos.inventario.event;

/** La planificación lo escucha para generar de inmediato el primer ciclo automático (RF27) */
public record ConfiguracionMantenimientoActualizada(Integer idServidor, Integer idGrupo) {
}
