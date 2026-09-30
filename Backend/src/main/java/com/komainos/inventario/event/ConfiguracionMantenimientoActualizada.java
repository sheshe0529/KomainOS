package com.komainos.inventario.event;

/**
 * Evento de dominio: se creo o modifico la configuracion de mantenimiento de un
 * servidor o de un grupo (RF17, RF70). La planificacion lo escucha para
 * generar de inmediato el primer ciclo en modalidad automatica (RF27), sin
 * esperar a la siguiente corrida del proceso programado.
 */
public record ConfiguracionMantenimientoActualizada(Integer idServidor, Integer idGrupo) {
}
