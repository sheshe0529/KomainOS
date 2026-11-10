package com.komainos.ejecucion.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcesoAnsibleTest {

    private static final Duration PLAZO = Duration.ofSeconds(15);

    @Test
    void conservaLaSalidaYElCodigoDeRetorno() throws Exception {
        Process proceso = ejecutar("salida");
        try {
            var salida = ProcesoAnsible.esperar(proceso, PLAZO, 5);
            assertThat(salida.codigo()).isEqualTo(7);
            assertThat(salida.texto()).contains("ok", "err");
        } finally {
            proceso.destroyForcibly();
        }
    }

    @Test
    void cancelaMientrasElProcesoSigueVivoSiExcedeLaSalida() throws Exception {
        Process proceso = ejecutar("exceder");
        try {
            assertThatThrownBy(() -> ProcesoAnsible.esperar(proceso, PLAZO, 1024))
                    .isInstanceOf(ProcesoAnsible.SalidaExcedida.class);
            assertThat(proceso.isAlive()).isFalse();
        } finally {
            proceso.destroyForcibly();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detieneElArbolAlVencerElPlazoOInterrumpir(boolean interrumpir) throws Exception {
        Process proceso = ejecutar("descendiente");
        ProcessHandle hijo = null;
        try {
            hijo = esperarDescendiente(proceso);
            if (interrumpir) {
                Thread.currentThread().interrupt();
            }
            assertThatThrownBy(() -> ProcesoAnsible.esperar(proceso, Duration.ofMillis(100), 1024))
                    .isInstanceOf(interrumpir ? InterruptedException.class : TimeoutException.class);
            assertThat(proceso.isAlive()).isFalse();
            assertThat(hijo.isAlive()).isFalse();
        } finally {
            Thread.interrupted();
            if (hijo != null) {
                hijo.destroyForcibly();
            }
            proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            proceso.destroyForcibly();
        }
    }

    private static ProcessHandle esperarDescendiente(Process proceso) throws InterruptedException {
        long vence = System.nanoTime() + PLAZO.toNanos();
        while (System.nanoTime() < vence && proceso.isAlive()) {
            var hijo = proceso.descendants().findFirst();
            if (hijo.isPresent()) {
                return hijo.get();
            }
            Thread.sleep(25);
        }
        throw new AssertionError("El proceso de prueba no inició su descendiente");
    }

    private static Process ejecutar(String modo) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), Programa.class.getName(), modo)
                .redirectErrorStream(true).start();
    }

    public static class Programa {

        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "salida" -> {
                    System.out.print("ok");
                    System.err.print("err");
                    System.exit(7);
                }
                case "exceder" -> System.out.write(new byte[16384]);
                case "descendiente" -> ejecutar("esperar");
                default -> { }
            }
            Thread.sleep(60_000);
        }
    }
}
