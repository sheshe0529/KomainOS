package com.komainos.inventario.model;

/**
 * Lo que decide qué excepción aplica a un servidor evaluado. idGrupo es el grupo de la orden grupal en ejecución, nulo
 * en un mantenimiento individual o en la revisión periódica: no son los grupos a los que pertenece el servidor.
 * idSistemaOperativo y familia son los del servidor: eligen las excepciones por sistema operativo y las métricas que
 * informa su análisis (R3.1). Sin familia se consideran todas las métricas.
 */
public record ContextoEvaluacion(Integer idServidor, Integer idGrupo, Integer idEntorno, Integer idNivelCriticidad,
                                 Integer idSistemaOperativo, FamiliaSistemaOperativo familia) {

    public static ContextoEvaluacion individual(Servidor servidor) {
        return enGrupo(servidor, null);
    }

    /** Quien lo construye garantiza que el servidor integra la orden de ese grupo (orden_detalle) */
    public static ContextoEvaluacion enGrupo(Servidor servidor, Integer idGrupo) {
        SistemaOperativo so = servidor.getVersionSistemaOperativo().getSistemaOperativo();
        return new ContextoEvaluacion(servidor.getId(), idGrupo, servidor.getEntorno().getId(),
                servidor.getNivelCriticidad().getId(), so.getId(), so.getFamilia());
    }

    /** Si la métrica del umbral se mide en este servidor: en otra familia ni se exige ni se evalúa */
    public boolean mide(Metrica metrica) {
        return metrica.aplicaA(familia);
    }
}
