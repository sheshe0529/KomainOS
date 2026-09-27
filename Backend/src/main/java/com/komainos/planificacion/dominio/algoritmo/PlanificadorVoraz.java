package com.komainos.planificacion.dominio.algoritmo;

import com.komainos.inventario.dominio.ModoEjecucion;
import com.komainos.planificacion.dominio.algoritmo.ResultadoPlanificacion.Tramo;
import com.komainos.shared.dominio.Intervalo;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Algoritmo voraz que determina la fecha programada de una orden (RF28).
 *
 * <p>Implementa la especificacion de
 * {@code Documentos/Decisiones/Algoritmo_planificacion_voraz.md}: recorre las
 * ventanas permisivas concretas desde el inicio mas temprano y devuelve el
 * <b>primer</b> inicio que respeta la ventana, la exclusion por servidor
 * (RF46, RF51) y la capacidad (RF68). Los unicos inicios que hace falta probar
 * son el comienzo de cada ventana y los instantes en que un tramo propio
 * empezaria justo cuando termina una reserva existente (seccion 5.4).
 *
 * <p>Es una clase pura, sin Spring ni JPA: recibe todo lo que necesita y no
 * consulta la base, lo que permite probar cada regla con datos literales.
 */
public final class PlanificadorVoraz {

    /** Modo automatico y reprogramacion sin fecha (especificacion, seccion 8). */
    public ResultadoPlanificacion planificar(SolicitudPlanificacion s, List<Reserva> reservas) {
        Forma forma = Forma.de(s);
        Duration total = forma.total();
        Instant inicioMasTemprano = redondearAlMinuto(
                max(s.fechaObjetivo(), s.ahora().plus(s.plazoAutorizacion())));

        List<Intervalo> ventanas = s.ventana().proyectar(
                inicioMasTemprano, inicioMasTemprano.plus(s.horizonte()).plus(total), s.zona());
        exigirVentanaUtil(s, ventanas, total);

        for (Intervalo ventana : ventanas) {
            Instant inicioMinimo = max(ventana.inicio(), inicioMasTemprano);
            Instant ultimoInicio = ventana.fin().minus(total);
            if (inicioMinimo.isAfter(ultimoInicio)) {
                continue;
            }
            List<Reserva> relevantes = reservas.stream()
                    .filter(r -> r.intervalo().seSolapaCon(ventana))
                    .toList();

            TreeSet<Instant> candidatos = new TreeSet<>();
            candidatos.add(inicioMinimo);
            for (Reserva r : relevantes) {
                for (Duration desplazamiento : forma.desplazamientos()) {
                    Instant candidato = r.intervalo().fin().minus(desplazamiento);
                    if (!candidato.isBefore(inicioMinimo) && !candidato.isAfter(ultimoInicio)) {
                        candidatos.add(candidato);
                    }
                }
            }

            for (Instant t : candidatos) {
                if (conflictos(forma, t, relevantes).isEmpty() && respetaCapacidad(forma, t, relevantes, s.capacidad())) {
                    Instant evaluacion = max(t.minus(s.plazoAutorizacion()), s.ahora());
                    return resultado(s, forma, t, ventana, evaluacion);
                }
            }
        }
        throw new PlanificacionImposibleException(("No existe un intervalo disponible en los próximos %d días que respete "
                + "la ventana permisiva, la capacidad y las órdenes ya programadas").formatted(s.horizonte().toDays()));
    }

    /**
     * Programacion manual con fecha y hora (RF30): no busca, verifica que el
     * inicio solicitado cumpla ventana, conflictos y capacidad (DEC-23).
     */
    public ResultadoPlanificacion verificar(SolicitudPlanificacion s, Instant inicio, List<Reserva> reservas) {
        Instant t = inicio.truncatedTo(ChronoUnit.SECONDS);
        if (!t.isAfter(s.ahora())) {
            throw new PlanificacionImposibleException("La fecha y hora de inicio deben ser futuras");
        }
        Forma forma = Forma.de(s);
        Intervalo propuesta = new Intervalo(t, t.plus(forma.total()));

        List<Intervalo> ventanas = s.ventana().proyectar(
                t.minus(Duration.ofDays(8)), propuesta.fin().plus(Duration.ofDays(1)), s.zona());
        exigirVentanaUtil(s, ventanas, forma.total());
        Intervalo ventana = ventanas.stream()
                .filter(v -> v.contiene(propuesta))
                .findFirst()
                .orElseThrow(() -> new PlanificacionImposibleException(
                        ("El mantenimiento (%d min) no cabe completo dentro de la ventana permisiva a partir de la hora "
                                + "indicada").formatted(forma.total().toMinutes())));

        List<Reserva> relevantes = reservas.stream().filter(r -> r.intervalo().seSolapaCon(propuesta)).toList();
        Set<String> enConflicto = conflictos(forma, t, relevantes);
        if (!enConflicto.isEmpty()) {
            throw new PlanificacionImposibleException(
                    "El horario se superpone con otra orden sobre el mismo servidor: " + String.join(", ", enConflicto));
        }
        if (!respetaCapacidad(forma, t, relevantes, s.capacidad())) {
            throw new PlanificacionImposibleException(
                    "El horario supera la concurrencia máxima de %d servidores en ejecución".formatted(s.capacidad()));
        }
        Instant evaluacion = max(t.minus(s.plazoAutorizacion()), s.ahora());
        return resultado(s, forma, t, ventana, evaluacion);
    }

    // ------------------------------------------------------------------ reglas

    /** Seccion 5.2: codigos de las ordenes que ocupan un servidor propio en ese horario. */
    private static Set<String> conflictos(Forma forma, Instant t, List<Reserva> reservas) {
        Set<String> codigos = new LinkedHashSet<>();
        for (TramoRelativo tramo : forma.tramos()) {
            Intervalo propio = tramo.en(t, forma.duracionPorServidor());
            for (Reserva r : reservas) {
                if (r.idServidor().equals(tramo.idServidor()) && r.intervalo().seSolapaCon(propio)) {
                    codigos.add(r.codigoOrden());
                }
            }
        }
        return codigos;
    }

    /**
     * Seccion 5.3: en ningun instante se superan C servidores en ejecucion.
     * Basta revisar los instantes en que algo empieza dentro de la orden.
     */
    private static boolean respetaCapacidad(Forma forma, Instant t, List<Reserva> reservas, int capacidad) {
        Intervalo orden = new Intervalo(t, t.plus(forma.total()));
        List<Intervalo> propios = forma.tramos().stream().map(tr -> tr.en(t, forma.duracionPorServidor())).toList();

        Set<Instant> instantes = new TreeSet<>();
        propios.forEach(p -> instantes.add(p.inicio()));
        reservas.stream().map(r -> r.intervalo().inicio()).filter(orden::contiene).forEach(instantes::add);

        for (Instant x : instantes) {
            long ocupados = reservas.stream().filter(r -> r.intervalo().contiene(x)).count()
                    + propios.stream().filter(p -> p.contiene(x)).count();
            if (ocupados > capacidad) {
                return false;
            }
        }
        return true;
    }

    private static void exigirVentanaUtil(SolicitudPlanificacion s, List<Intervalo> ventanas, Duration total) {
        if (s.ventana().estaVacio() || ventanas.isEmpty()) {
            throw new PlanificacionImposibleException(s.esGrupal()
                    ? "Los integrantes del grupo no comparten ningún intervalo de ventana permisiva"
                    : "El servidor no tiene una ventana permisiva válida");
        }
        Duration masLarga = s.ventana().tramoContinuoMasLargo();
        if (masLarga.compareTo(total) < 0) {
            throw new PlanificacionImposibleException(
                    "La ventana permisiva más amplia (%d min) es menor que la duración a reservar (%d min)"
                            .formatted(masLarga.toMinutes(), total.toMinutes()));
        }
    }

    private static ResultadoPlanificacion resultado(SolicitudPlanificacion s, Forma forma, Instant t,
                                                    Intervalo ventana, Instant evaluacion) {
        List<Tramo> tramos = forma.tramos().stream()
                .map(tr -> {
                    Intervalo i = tr.en(t, forma.duracionPorServidor());
                    return new Tramo(tr.idServidor(), tr.posicion(), i.inicio(), i.fin());
                })
                .toList();
        return new ResultadoPlanificacion(s.fechaObjetivo(), t, t.plus(forma.total()), evaluacion, ventana, tramos);
    }

    private static Instant redondearAlMinuto(Instant instante) {
        Instant truncado = instante.truncatedTo(ChronoUnit.MINUTES);
        return truncado.equals(instante) ? instante : truncado.plus(1, ChronoUnit.MINUTES);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    // --------------------------------------------------- forma de la reserva

    /** Tramo de un servidor expresado como desplazamiento desde el inicio de la orden. */
    record TramoRelativo(Integer idServidor, int posicion, Duration desplazamiento) {
        Intervalo en(Instant inicioOrden, Duration duracion) {
            Instant inicio = inicioOrden.plus(desplazamiento);
            return new Intervalo(inicio, inicio.plus(duracion));
        }
    }

    /**
     * Seccion 4: individual, un tramo; SECUENCIAL, uno detras de otro;
     * PARALELO, un tramo piloto y luego oleadas de a lo sumo C servidores
     * (RF58, DEC-11).
     */
    record Forma(List<TramoRelativo> tramos, Duration duracionPorServidor, Duration total) {

        static Forma de(SolicitudPlanificacion s) {
            Duration d = s.duracionPorServidor();
            List<Integer> ids = s.idsServidores();
            List<TramoRelativo> tramos = new ArrayList<>();
            if (!s.esGrupal() || ids.size() == 1) {
                tramos.add(new TramoRelativo(ids.getFirst(), 1, Duration.ZERO));
                return new Forma(tramos, d, d);
            }
            if (s.modoEjecucion() == ModoEjecucion.SECUENCIAL) {
                for (int i = 0; i < ids.size(); i++) {
                    tramos.add(new TramoRelativo(ids.get(i), i + 1, d.multipliedBy(i)));
                }
                return new Forma(tramos, d, d.multipliedBy(ids.size()));
            }
            tramos.add(new TramoRelativo(ids.getFirst(), 1, Duration.ZERO));
            int oleadas = 0;
            for (int j = 1; j < ids.size(); j++) {
                int oleada = 1 + (j - 1) / s.capacidad();
                oleadas = Math.max(oleadas, oleada);
                tramos.add(new TramoRelativo(ids.get(j), j + 1, d.multipliedBy(oleada)));
            }
            return new Forma(tramos, d, d.multipliedBy(1L + oleadas));
        }

        Set<Duration> desplazamientos() {
            Set<Duration> distintos = new TreeSet<>();
            tramos.forEach(t -> distintos.add(t.desplazamiento()));
            return distintos;
        }
    }
}
