package com.komainos.inventario.model;

/**
 * Lo que decide qué excepción aplica a un servidor evaluado. idGrupo es el grupo de la orden grupal en ejecución, nulo
 * en un mantenimiento individual o en la revisión periódica: no son los grupos a los que pertenece el servidor.
 */
public record ContextoEvaluacion(Integer idServidor, Integer idGrupo, Integer idEntorno, Integer idNivelCriticidad) {

    public static ContextoEvaluacion individual(Servidor servidor) {
        return new ContextoEvaluacion(servidor.getId(), null, servidor.getEntorno().getId(),
                servidor.getNivelCriticidad().getId());
    }

    /** Quien lo construye garantiza que el servidor integra la orden de ese grupo (orden_detalle) */
    public static ContextoEvaluacion enGrupo(Servidor servidor, Integer idGrupo) {
        return new ContextoEvaluacion(servidor.getId(), idGrupo, servidor.getEntorno().getId(),
                servidor.getNivelCriticidad().getId());
    }
}
