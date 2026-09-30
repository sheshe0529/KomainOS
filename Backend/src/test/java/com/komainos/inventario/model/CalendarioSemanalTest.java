package com.komainos.inventario.model;

import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.shared.model.Intervalo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static com.komainos.inventario.model.DiaSemana.DOMINGO;
import static com.komainos.inventario.model.DiaSemana.JUEVES;
import static com.komainos.inventario.model.DiaSemana.LUNES;
import static com.komainos.inventario.model.DiaSemana.MARTES;
import static com.komainos.inventario.model.DiaSemana.MIERCOLES;
import static com.komainos.inventario.model.DiaSemana.SABADO;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ventana permisiva efectiva (RF18, RF19, RF21, HU14)")
class CalendarioSemanalTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static IntervaloSemanal intervalo(DiaSemana di, String hi, DiaSemana df, String hf) {
        return new IntervaloSemanal(di, LocalTime.parse(hi), df, LocalTime.parse(hf));
    }

    private static CalendarioSemanal calendario(IntervaloSemanal... intervalos) {
        return CalendarioSemanal.deIntervalos(List.of(intervalos));
    }

    private static java.time.Instant lima(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(LIMA).toInstant();
    }

    @Nested
    @DisplayName("Intervalos que cruzan la medianoche")
    class CruceDeMedianoche {

        @Test
        @DisplayName("sábado 22:00 a domingo 02:00 dura 4 horas")
        void cruzaMedianoche() {
            var c = calendario(intervalo(SABADO, "22:00", DOMINGO, "02:00"));
            assertThat(c.tramoContinuoMasLargo()).isEqualTo(Duration.ofHours(4));
        }

        @Test
        @DisplayName("domingo 23:00 a lunes 01:00 se proyecta como un solo intervalo concreto")
        void cruzaDeDomingoALunes() {
            var c = calendario(intervalo(DOMINGO, "23:00", LUNES, "01:00"));
            // Semana del lunes 2026-09-28: el domingo 2026-10-04 23:00 empalma con el lunes 05.
            List<Intervalo> concretos = c.proyectar(lima("2026-10-04T00:00"), lima("2026-10-06T00:00"), LIMA);
            assertThat(concretos).containsExactly(
                    new Intervalo(lima("2026-10-04T23:00"), lima("2026-10-05T01:00")));
            assertThat(c.tramoContinuoMasLargo()).isEqualTo(Duration.ofHours(2));
        }
    }

    @Nested
    @DisplayName("HU14 CA5: intervalos contiguos entre días son continuos")
    class Contiguos {

        @Test
        @DisplayName("martes 22:00-00:00 y miércoles 00:00-02:00 permiten martes 23:00 a miércoles 01:00")
        void seFusionan() {
            var c = calendario(
                    intervalo(MARTES, "22:00", MIERCOLES, "00:00"),
                    intervalo(MIERCOLES, "00:00", MIERCOLES, "02:00"));

            assertThat(c.tramoContinuoMasLargo()).isEqualTo(Duration.ofHours(4));
            List<Intervalo> concretos = c.proyectar(lima("2026-09-29T00:00"), lima("2026-09-30T12:00"), LIMA);
            assertThat(concretos).hasSize(1);
            assertThat(concretos.getFirst().contiene(
                    new Intervalo(lima("2026-09-29T23:00"), lima("2026-09-30T01:00")))).isTrue();
        }

        @Test
        @DisplayName("se presentan como un único intervalo")
        void sePresentanUnidos() {
            var c = calendario(
                    intervalo(MARTES, "22:00", MIERCOLES, "00:00"),
                    intervalo(MIERCOLES, "00:00", MIERCOLES, "02:00"));
            assertThat(c.aIntervalos()).containsExactly(intervalo(MARTES, "22:00", MIERCOLES, "02:00"));
        }

        @Test
        @DisplayName("un tramo que termina el domingo a medianoche y otro que empieza el lunes 00:00 son uno solo")
        void empalmeDeSemana() {
            var c = calendario(
                    intervalo(DOMINGO, "22:00", LUNES, "00:00"),
                    intervalo(LUNES, "00:00", LUNES, "03:00"));
            assertThat(c.tramoContinuoMasLargo()).isEqualTo(Duration.ofHours(5));
            assertThat(c.aIntervalos()).containsExactly(intervalo(DOMINGO, "22:00", LUNES, "03:00"));
        }
    }

    @Nested
    @DisplayName("RF21: la ventana del grupo es la intersección de sus integrantes")
    class Interseccion {

        @Test
        @DisplayName("solo quedan los minutos presentes en todas las ventanas")
        void interseca() {
            var a = calendario(intervalo(SABADO, "00:00", SABADO, "06:00"));
            var b = calendario(intervalo(SABADO, "02:00", SABADO, "08:00"));
            var c = calendario(intervalo(SABADO, "01:00", SABADO, "05:00"));

            var grupo = CalendarioSemanal.interseccionDe(List.of(a, b, c));

            assertThat(grupo.aIntervalos()).containsExactly(intervalo(SABADO, "02:00", SABADO, "05:00"));
        }

        @Test
        @DisplayName("sin minutos en común la ventana del grupo es vacía")
        void disjuntas() {
            var a = calendario(intervalo(SABADO, "00:00", SABADO, "02:00"));
            var b = calendario(intervalo(JUEVES, "00:00", JUEVES, "02:00"));
            assertThat(a.interseccion(b).estaVacio()).isTrue();
        }

        @Test
        @DisplayName("la intersección respeta los intervalos que cruzan la semana")
        void intersecaCruceDeSemana() {
            var a = calendario(intervalo(DOMINGO, "20:00", LUNES, "04:00"));
            var b = calendario(intervalo(DOMINGO, "23:00", LUNES, "06:00"));
            assertThat(a.interseccion(b).aIntervalos()).containsExactly(intervalo(DOMINGO, "23:00", LUNES, "04:00"));
        }
    }

    @Test
    @DisplayName("la proyección devuelve cada ocurrencia semanal dentro del rango")
    void proyectaVariasSemanas() {
        var c = calendario(intervalo(SABADO, "01:00", SABADO, "05:00"));
        List<Intervalo> concretos = c.proyectar(lima("2026-10-01T00:00"), lima("2026-10-15T00:00"), LIMA);
        assertThat(concretos).containsExactly(
                new Intervalo(lima("2026-10-03T01:00"), lima("2026-10-03T05:00")),
                new Intervalo(lima("2026-10-10T01:00"), lima("2026-10-10T05:00")));
    }

    @Test
    @DisplayName("una ventana sin intervalos no produce ocurrencias")
    void vacia() {
        assertThat(CalendarioSemanal.vacio().proyectar(lima("2026-10-01T00:00"), lima("2026-12-01T00:00"), LIMA))
                .isEmpty();
        assertThat(CalendarioSemanal.vacio().tramoContinuoMasLargo()).isEqualTo(Duration.ZERO);
    }
}
