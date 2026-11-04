package com.komainos.mantenimiento.model;

import java.util.List;

/**
 * Lo que dura el plan de trabajo completo (RF41, RF58), con la misma forma que usa la planificación (DEC-11): en
 * secuencia, una MOP tras otra; en paralelo, primero el piloto y después los demás en oleadas de hasta la concurrencia
 * máxima, cada oleada tan larga como su MOP más larga.
 */
public final class DuracionPlan {

    private DuracionPlan() {
    }

    /** La MOP de un servidor: lo que dura y si es el piloto */
    record Tramo(int minutos, boolean piloto) {
    }

    /** @param planes en el orden de ejecución */
    public static int estimar(List<Mop> planes, boolean paralelo, int capacidad) {
        return estimarTramos(planes.stream()
                .map(m -> new Tramo(m.duracionTotal(), m.getDetalle().isEsServidorPiloto())).toList(), paralelo, capacidad);
    }

    static int estimarTramos(List<Tramo> tramos, boolean paralelo, int capacidad) {
        if (!paralelo) {
            return tramos.stream().mapToInt(Tramo::minutos).sum();
        }
        int total = tramos.stream().filter(Tramo::piloto).mapToInt(Tramo::minutos).sum();
        List<Integer> demas = tramos.stream().filter(t -> !t.piloto()).map(Tramo::minutos).toList();
        int porOleada = Math.max(1, capacidad);
        for (int i = 0; i < demas.size(); i += porOleada) {
            total += demas.subList(i, Math.min(i + porOleada, demas.size())).stream()
                    .mapToInt(Integer::intValue).max().orElse(0);
        }
        return total;
    }
}
