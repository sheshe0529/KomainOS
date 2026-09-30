package com.komainos.planificacion.service.algoritmo;

import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.planificacion.service.algoritmo.ResultadoPlanificacion.Tramo;
import com.komainos.shared.model.Intervalo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.komainos.inventario.model.DiaSemana.DOMINGO;
import static com.komainos.inventario.model.DiaSemana.MARTES;
import static com.komainos.inventario.model.DiaSemana.MIERCOLES;
import static com.komainos.inventario.model.DiaSemana.SABADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Algoritmo voraz de la fecha programada (RF28). Cada prueba corresponde a una
 * regla de Documentos/Decisiones/Algoritmo_planificacion_voraz.md.
 *
 * <p>Referencia temporal: "ahora" es el lunes 2026-09-28 09:00 en Lima; el
 * primer sabado es el 2026-10-03.
 */
@DisplayName("Algoritmo voraz de planificación (RF28)")
class PlanificadorVorazTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant AHORA = lima("2026-09-28T09:00");
    private static final Duration CUATRO_HORAS = Duration.ofMinutes(240);

    private final PlanificadorVoraz planificador = new PlanificadorVoraz();

    private static Instant lima(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(LIMA).toInstant();
    }

    private static CalendarioSemanal ventana(DiaSemana di, String hi, DiaSemana df, String hf) {
        return CalendarioSemanal.deIntervalos(List.of(
                new IntervaloSemanal(di, LocalTime.parse(hi), df, LocalTime.parse(hf))));
    }

    private static SolicitudPlanificacion individual(CalendarioSemanal ventana, Instant objetivo, int capacidad) {
        return new SolicitudPlanificacion(List.of(1), null, objetivo, Duration.ofHours(24), ventana,
                CUATRO_HORAS, capacidad, AHORA, LIMA, Duration.ofDays(365));
    }

    private static Reserva reserva(int idServidor, String desde, String hasta, String codigo) {
        return new Reserva(idServidor, new Intervalo(lima(desde), lima(hasta)), 99, codigo);
    }

    @Nested
    @DisplayName("Primer intervalo disponible desde la fecha objetivo")
    class PrimerAjuste {

        @Test
        @DisplayName("sin otras órdenes, toma el inicio de la primera ventana")
        void primeraVentana() {
            var r = planificador.planificar(individual(ventana(SABADO, "01:00", SABADO, "06:00"), AHORA, 10), List.of());

            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T01:00"));
            assertThat(r.fin()).isEqualTo(lima("2026-10-03T05:00"));
            assertThat(r.ventanaAplicada()).isEqualTo(new Intervalo(lima("2026-10-03T01:00"), lima("2026-10-03T06:00")));
            // RF38: la evaluacion previa empieza el plazo de autorizacion antes.
            assertThat(r.fechaEvaluacion()).isEqualTo(lima("2026-10-02T01:00"));
            assertThat(r.tramos()).containsExactly(new Tramo(1, 1, r.inicio(), r.fin()));
        }

        @Test
        @DisplayName("no busca antes de la fecha objetivo")
        void respetaFechaObjetivo() {
            var r = planificador.planificar(
                    individual(ventana(SABADO, "01:00", SABADO, "06:00"), lima("2026-10-05T00:00"), 10), List.of());
            assertThat(r.inicio()).isEqualTo(lima("2026-10-10T01:00"));
        }

        @Test
        @DisplayName("DEC-10: si la ventana cae antes de ahora + plazo de autorización, pasa a la siguiente")
        void respetaPlazoDeAutorizacion() {
            // Ahora viernes 12:00 con plazo de 24 h: el sabado 01:00 queda dentro del plazo.
            var s = new SolicitudPlanificacion(List.of(1), null, lima("2026-10-02T12:00"), Duration.ofHours(24),
                    ventana(SABADO, "01:00", SABADO, "06:00"), CUATRO_HORAS, 10, lima("2026-10-02T12:00"), LIMA,
                    Duration.ofDays(365));
            assertThat(planificador.planificar(s, List.of()).inicio()).isEqualTo(lima("2026-10-10T01:00"));
        }

        @Test
        @DisplayName("una fecha objetivo dentro de la ventana empieza en ese minuto")
        void empiezaDentroDeLaVentana() {
            var r = planificador.planificar(
                    individual(ventana(SABADO, "00:00", SABADO, "12:00"), lima("2026-10-03T02:30:20"), 10), List.of());
            // Se redondea al minuto siguiente: TIMESTAMP(0) y horarios legibles.
            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T02:31"));
        }
    }

    @Nested
    @DisplayName("RF46/RF51: nunca dos intervenciones simultáneas sobre un servidor")
    class Conflictos {

        @Test
        @DisplayName("si el servidor ya está reservado, empieza cuando termina esa reserva")
        void esperaAlFinDeLaReserva() {
            var r = planificador.planificar(individual(ventana(SABADO, "01:00", SABADO, "07:00"), AHORA, 10),
                    List.of(reserva(1, "2026-10-03T01:00", "2026-10-03T03:00", "OM-2026-0001")));
            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T03:00"));
        }

        @Test
        @DisplayName("si no cabe después de la reserva, pasa a la siguiente ventana")
        void siguienteVentana() {
            var r = planificador.planificar(individual(ventana(SABADO, "01:00", SABADO, "06:00"), AHORA, 10),
                    List.of(reserva(1, "2026-10-03T02:00", "2026-10-03T04:00", "OM-2026-0001")));
            assertThat(r.inicio()).isEqualTo(lima("2026-10-10T01:00"));
        }

        @Test
        @DisplayName("la reserva de otro servidor no bloquea si hay capacidad")
        void otroServidorNoBloquea() {
            var r = planificador.planificar(individual(ventana(SABADO, "01:00", SABADO, "06:00"), AHORA, 10),
                    List.of(reserva(2, "2026-10-03T01:00", "2026-10-03T05:00", "OM-2026-0002")));
            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T01:00"));
        }
    }

    @Nested
    @DisplayName("RF68: concurrencia máxima")
    class Capacidad {

        @Test
        @DisplayName("con capacidad 1, espera a que termine la ejecución de otro servidor")
        void respetaCapacidad() {
            var r = planificador.planificar(individual(ventana(SABADO, "01:00", SABADO, "07:00"), AHORA, 1),
                    List.of(reserva(2, "2026-10-03T01:00", "2026-10-03T03:00", "OM-2026-0002")));
            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T03:00"));
        }

        @Test
        @DisplayName("sin capacidad en todo el horizonte informa que no hay intervalo")
        void horizonteAgotado() {
            var s = new SolicitudPlanificacion(List.of(1), null, AHORA, Duration.ofHours(24),
                    ventana(SABADO, "01:00", SABADO, "06:00"), CUATRO_HORAS, 1, AHORA, LIMA, Duration.ofDays(20));
            List<Reserva> ocupado = List.of(reserva(2, "2026-09-28T00:00", "2026-12-31T00:00", "OM-2026-0002"));

            assertThatThrownBy(() -> planificador.planificar(s, ocupado))
                    .isInstanceOf(PlanificacionImposibleException.class)
                    .hasMessageContaining("próximos 20 días");
        }
    }

    @Nested
    @DisplayName("Ventana permisiva efectiva")
    class Ventanas {

        @Test
        @DisplayName("admite intervalos que cruzan la medianoche")
        void cruzaMedianoche() {
            var r = planificador.planificar(individual(ventana(SABADO, "22:00", DOMINGO, "04:00"), AHORA, 10), List.of());
            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T22:00"));
            assertThat(r.fin()).isEqualTo(lima("2026-10-04T02:00"));
        }

        @Test
        @DisplayName("HU14 CA5: intervalos contiguos entre días forman uno continuo")
        void contiguos() {
            var cal = CalendarioSemanal.deIntervalos(List.of(
                    new IntervaloSemanal(MARTES, LocalTime.of(22, 0), MIERCOLES, LocalTime.MIDNIGHT),
                    new IntervaloSemanal(MIERCOLES, LocalTime.MIDNIGHT, MIERCOLES, LocalTime.of(2, 0))));
            var r = planificador.planificar(individual(cal, AHORA, 10), List.of());
            assertThat(r.inicio()).isEqualTo(lima("2026-09-29T22:00"));
            assertThat(r.fin()).isEqualTo(lima("2026-09-30T02:00"));
        }

        @Test
        @DisplayName("sin ventana no se planifica")
        void sinVentana() {
            assertThatThrownBy(() -> planificador.planificar(individual(CalendarioSemanal.vacio(), AHORA, 10), List.of()))
                    .isInstanceOf(PlanificacionImposibleException.class)
                    .hasMessageContaining("ventana permisiva válida");
        }

        @Test
        @DisplayName("una ventana más corta que la duración a reservar no admite el mantenimiento")
        void ventanaCorta() {
            assertThatThrownBy(() -> planificador.planificar(
                    individual(ventana(SABADO, "01:00", SABADO, "03:00"), AHORA, 10), List.of()))
                    .isInstanceOf(PlanificacionImposibleException.class)
                    .hasMessageContaining("120 min")
                    .hasMessageContaining("240 min");
        }
    }

    @Nested
    @DisplayName("Órdenes grupales")
    class Grupos {

        @Test
        @DisplayName("SECUENCIAL: un servidor detrás de otro dentro de la misma ventana")
        void secuencial() {
            var s = new SolicitudPlanificacion(List.of(1, 2, 3), ModoEjecucion.SECUENCIAL, AHORA, Duration.ofHours(24),
                    ventana(SABADO, "00:00", SABADO, "12:00"), Duration.ofMinutes(60), 10, AHORA, LIMA, Duration.ofDays(365));
            var r = planificador.planificar(s, List.of());

            assertThat(r.tramos()).containsExactly(
                    new Tramo(1, 1, lima("2026-10-03T00:00"), lima("2026-10-03T01:00")),
                    new Tramo(2, 2, lima("2026-10-03T01:00"), lima("2026-10-03T02:00")),
                    new Tramo(3, 3, lima("2026-10-03T02:00"), lima("2026-10-03T03:00")));
            assertThat(r.fin()).isEqualTo(lima("2026-10-03T03:00"));
        }

        @Test
        @DisplayName("PARALELO: piloto primero y luego oleadas que no superan la capacidad")
        void paraleloEnOleadas() {
            var s = new SolicitudPlanificacion(List.of(1, 2, 3, 4), ModoEjecucion.PARALELO, AHORA, Duration.ofHours(24),
                    ventana(SABADO, "00:00", SABADO, "12:00"), Duration.ofMinutes(60), 2, AHORA, LIMA, Duration.ofDays(365));
            var r = planificador.planificar(s, List.of());

            assertThat(r.tramos()).containsExactly(
                    new Tramo(1, 1, lima("2026-10-03T00:00"), lima("2026-10-03T01:00")),
                    new Tramo(2, 2, lima("2026-10-03T01:00"), lima("2026-10-03T02:00")),
                    new Tramo(3, 3, lima("2026-10-03T01:00"), lima("2026-10-03T02:00")),
                    new Tramo(4, 4, lima("2026-10-03T02:00"), lima("2026-10-03T03:00")));
        }

        @Test
        @DisplayName("RF46: el tramo de un integrante evita las reservas individuales de ese servidor")
        void grupoRespetaOrdenIndividual() {
            var s = new SolicitudPlanificacion(List.of(1, 2), ModoEjecucion.SECUENCIAL, AHORA, Duration.ofHours(24),
                    ventana(SABADO, "00:00", SABADO, "12:00"), Duration.ofMinutes(60), 10, AHORA, LIMA, Duration.ofDays(365));
            // El servidor 2 (segundo tramo, desplazamiento 1 h) esta ocupado hasta las 03:00.
            var r = planificador.planificar(s, List.of(reserva(2, "2026-10-03T00:30", "2026-10-03T03:00", "OM-2026-0007")));

            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T02:00"));
            assertThat(r.tramos().get(1).inicio()).isEqualTo(lima("2026-10-03T03:00"));
        }
    }

    @Nested
    @DisplayName("RF30: programación manual con fecha y hora")
    class Verificacion {

        private final SolicitudPlanificacion solicitud =
                individual(ventana(SABADO, "01:00", SABADO, "07:00"), AHORA, 10);

        @Test
        @DisplayName("acepta un horario dentro de la ventana y libre")
        void acepta() {
            var r = planificador.verificar(solicitud, lima("2026-10-03T02:00"), List.of());
            assertThat(r.inicio()).isEqualTo(lima("2026-10-03T02:00"));
            assertThat(r.fin()).isEqualTo(lima("2026-10-03T06:00"));
        }

        @Test
        @DisplayName("rechaza un horario que no cabe completo en la ventana")
        void fueraDeVentana() {
            assertThatThrownBy(() -> planificador.verificar(solicitud, lima("2026-10-03T04:00"), List.of()))
                    .isInstanceOf(PlanificacionImposibleException.class)
                    .hasMessageContaining("ventana permisiva");
        }

        @Test
        @DisplayName("informa la orden con la que se superpone")
        void conflicto() {
            List<Reserva> reservas = new ArrayList<>();
            reservas.add(reserva(1, "2026-10-03T03:00", "2026-10-03T04:00", "OM-2026-0042"));
            assertThatThrownBy(() -> planificador.verificar(solicitud, lima("2026-10-03T02:00"), reservas))
                    .isInstanceOf(PlanificacionImposibleException.class)
                    .hasMessageContaining("OM-2026-0042");
        }

        @Test
        @DisplayName("rechaza fechas pasadas")
        void pasado() {
            assertThatThrownBy(() -> planificador.verificar(solicitud, lima("2026-09-26T02:00"), List.of()))
                    .isInstanceOf(PlanificacionImposibleException.class)
                    .hasMessageContaining("futuras");
        }

        @Test
        @DisplayName("DEC-23: si falta menos que el plazo, la evaluación queda en el instante actual")
        void evaluacionNoQuedaEnElPasado() {
            var cercana = new SolicitudPlanificacion(List.of(1), null, AHORA, Duration.ofHours(72),
                    ventana(SABADO, "01:00", SABADO, "07:00"), CUATRO_HORAS, 10, lima("2026-10-02T12:00"), LIMA,
                    Duration.ofDays(365));
            var r = planificador.verificar(cercana, lima("2026-10-03T01:00"), List.of());
            assertThat(r.fechaEvaluacion()).isEqualTo(lima("2026-10-02T12:00"));
        }
    }
}
