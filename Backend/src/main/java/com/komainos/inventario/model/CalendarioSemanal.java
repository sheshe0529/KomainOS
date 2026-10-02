package com.komainos.inventario.model;

import com.komainos.shared.model.Intervalo;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Ventana semanal inmutable, en minutos desde el lunes 00:00 de la zona operativa (DEC-06) */
public final class CalendarioSemanal {

    public static final int MINUTOS_DIA = 24 * 60;
    public static final int MINUTOS_SEMANA = 7 * MINUTOS_DIA;

    private final List<Tramo> tramos;

    private CalendarioSemanal(List<Tramo> tramos) {
        this.tramos = List.copyOf(tramos);
    }

    public static CalendarioSemanal vacio() {
        return new CalendarioSemanal(List.of());
    }

    public static CalendarioSemanal de(Collection<VentanaMantenimiento> ventanas) {
        List<Tramo> tramos = new ArrayList<>();
        for (VentanaMantenimiento v : ventanas) {
            agregar(tramos, v.getDiaInicio(), v.getHoraInicio(), v.getDiaFin(), v.getHoraFin());
        }
        return new CalendarioSemanal(fusionar(tramos));
    }

    public static CalendarioSemanal deIntervalos(List<IntervaloSemanal> intervalos) {
        List<Tramo> tramos = new ArrayList<>();
        for (IntervaloSemanal i : intervalos) {
            agregar(tramos, i.diaInicio(), i.horaInicio(), i.diaFin(), i.horaFin());
        }
        return new CalendarioSemanal(fusionar(tramos));
    }

    public boolean estaVacio() {
        return tramos.isEmpty();
    }

    List<Tramo> tramos() {
        return tramos;
    }

    public CalendarioSemanal interseccion(CalendarioSemanal otro) {
        List<Tramo> resultado = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < tramos.size() && j < otro.tramos.size()) {
            Tramo a = tramos.get(i);
            Tramo b = otro.tramos.get(j);
            int inicio = Math.max(a.inicio(), b.inicio());
            int fin = Math.min(a.fin(), b.fin());
            if (inicio < fin) {
                resultado.add(new Tramo(inicio, fin));
            }
            if (a.fin() < b.fin()) {
                i++;
            } else {
                j++;
            }
        }
        return new CalendarioSemanal(fusionar(resultado));
    }

    public static CalendarioSemanal interseccionDe(List<CalendarioSemanal> calendarios) {
        if (calendarios.isEmpty()) {
            return vacio();
        }
        CalendarioSemanal acumulado = calendarios.getFirst();
        for (int k = 1; k < calendarios.size(); k++) {
            acumulado = acumulado.interseccion(calendarios.get(k));
        }
        return acumulado;
    }

    /** El domingo empalma con el lunes: una ventana que cubre toda la semana no tiene fin */
    public Duration tramoContinuoMasLargo() {
        if (tramos.isEmpty()) {
            return Duration.ZERO;
        }
        if (cubreTodaLaSemana()) {
            return Duration.ofDays(3650);
        }
        int maximo = tramos.stream().mapToInt(Tramo::longitud).max().orElse(0);
        Tramo primero = tramos.getFirst();
        Tramo ultimo = tramos.getLast();
        if (primero.inicio() == 0 && ultimo.fin() == MINUTOS_SEMANA) {
            maximo = Math.max(maximo, primero.longitud() + ultimo.longitud());
        }
        return Duration.ofMinutes(maximo);
    }

    public boolean cubreTodaLaSemana() {
        return tramos.size() == 1 && tramos.getFirst().inicio() == 0 && tramos.getFirst().fin() == MINUTOS_SEMANA;
    }

    /** Los intervalos no se recortan: la planificación necesita sus límites reales */
    public List<Intervalo> proyectar(Instant desde, Instant hasta, ZoneId zona) {
        if (tramos.isEmpty() || !hasta.isAfter(desde)) {
            return List.of();
        }
        // Empieza una semana antes para capturar un tramo que venga cruzando desde la semana anterior
        LocalDate lunes = LocalDate.ofInstant(desde, zona)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        LocalDate limite = LocalDate.ofInstant(hasta, zona).plusWeeks(1);

        List<Intervalo> concretos = new ArrayList<>();
        for (LocalDate semana = lunes; !semana.isAfter(limite); semana = semana.plusWeeks(1)) {
            LocalDateTime base = semana.atStartOfDay();
            for (Tramo t : tramos) {
                Instant inicio = base.plusMinutes(t.inicio()).atZone(zona).toInstant();
                Instant fin = base.plusMinutes(t.fin()).atZone(zona).toInstant();
                if (fin.isAfter(inicio)) {
                    concretos.add(new Intervalo(inicio, fin));
                }
            }
        }
        concretos.sort(Comparator.comparing(Intervalo::inicio));

        List<Intervalo> fusionados = new ArrayList<>();
        for (Intervalo actual : concretos) {
            if (!fusionados.isEmpty() && !fusionados.getLast().fin().isBefore(actual.inicio())) {
                Intervalo previo = fusionados.removeLast();
                Instant fin = previo.fin().isAfter(actual.fin()) ? previo.fin() : actual.fin();
                fusionados.add(new Intervalo(previo.inicio(), fin));
            } else {
                fusionados.add(actual);
            }
        }
        return fusionados.stream()
                .filter(i -> i.fin().isAfter(desde) && i.inicio().isBefore(hasta))
                .toList();
    }

    /** Un tramo que termina el domingo a medianoche y otro que empieza el lunes 00:00 se muestran como uno solo */
    public List<IntervaloSemanal> aIntervalos() {
        if (tramos.isEmpty()) {
            return List.of();
        }
        List<Tramo> presentables = new ArrayList<>(tramos);
        if (presentables.size() > 1
                && presentables.getFirst().inicio() == 0
                && presentables.getLast().fin() == MINUTOS_SEMANA) {
            Tramo primero = presentables.removeFirst();
            Tramo ultimo = presentables.removeLast();
            presentables.add(new Tramo(ultimo.inicio(), MINUTOS_SEMANA + primero.fin()));
        }
        return presentables.stream()
                .sorted(Comparator.comparingInt(Tramo::inicio))
                .map(CalendarioSemanal::aIntervalo)
                .toList();
    }

    private static void agregar(List<Tramo> tramos, DiaSemana diaInicio, LocalTime horaInicio,
                                DiaSemana diaFin, LocalTime horaFin) {
        int inicio = diaInicio.ordinal() * MINUTOS_DIA + horaInicio.toSecondOfDay() / 60;
        int duracion = diaInicio.diasHasta(diaFin) * MINUTOS_DIA
                + (horaFin.toSecondOfDay() - horaInicio.toSecondOfDay()) / 60;
        if (duracion <= 0) {
            return;
        }
        int fin = inicio + duracion;
        if (fin <= MINUTOS_SEMANA) {
            tramos.add(new Tramo(inicio, fin));
        } else {
            // Cruza de domingo a lunes: se parte en dos tramos
            tramos.add(new Tramo(inicio, MINUTOS_SEMANA));
            tramos.add(new Tramo(0, fin - MINUTOS_SEMANA));
        }
    }

    private static List<Tramo> fusionar(List<Tramo> tramos) {
        List<Tramo> ordenados = new ArrayList<>(tramos);
        ordenados.sort(Comparator.comparingInt(Tramo::inicio));
        List<Tramo> resultado = new ArrayList<>();
        for (Tramo t : ordenados) {
            if (!resultado.isEmpty() && resultado.getLast().fin() >= t.inicio()) {
                Tramo previo = resultado.removeLast();
                resultado.add(new Tramo(previo.inicio(), Math.max(previo.fin(), t.fin())));
            } else {
                resultado.add(t);
            }
        }
        return resultado;
    }

    private static IntervaloSemanal aIntervalo(Tramo t) {
        int inicio = t.inicio();
        int fin = t.fin() % MINUTOS_SEMANA;
        DiaSemana diaInicio = DiaSemana.values()[inicio / MINUTOS_DIA];
        LocalTime horaInicio = LocalTime.ofSecondOfDay((inicio % MINUTOS_DIA) * 60L);
        // Un fin exacto a medianoche se muestra como 00:00 del día siguiente
        DiaSemana diaFin = DiaSemana.values()[(fin / MINUTOS_DIA) % 7];
        LocalTime horaFin = LocalTime.ofSecondOfDay((fin % MINUTOS_DIA) * 60L);
        return new IntervaloSemanal(diaInicio, horaInicio, diaFin, horaFin);
    }

    /** Tramo [inicio, fin) en minutos desde el lunes 00:00 */
    record Tramo(int inicio, int fin) {
        int longitud() {
            return fin - inicio;
        }
    }

    public record IntervaloSemanal(DiaSemana diaInicio, LocalTime horaInicio, DiaSemana diaFin, LocalTime horaFin) {
    }
}
