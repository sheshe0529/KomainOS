package com.komainos.ejecucion.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/** Acota la salida y la duración del proceso local antes de liberar sus archivos temporales. */
final class ProcesoAnsible {

    private static final long PAUSA_NANOS = TimeUnit.MILLISECONDS.toNanos(20);

    private ProcesoAnsible() {
    }

    static Salida esperar(Process proceso, Duration plazo, int maximoBytes)
            throws IOException, InterruptedException, TimeoutException {
        long vence = System.nanoTime() + TimeUnit.NANOSECONDS.convert(plazo);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] bloque = new byte[8192];
        boolean termino = false;
        boolean capturada = false;
        try (InputStream entrada = proceso.getInputStream()) {
            try {
                while (true) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    long restante = vence - System.nanoTime();
                    if (restante <= 0) {
                        throw new TimeoutException();
                    }
                    // Leer solo lo disponible permite comprobar el plazo incluso con descendientes que retienen la tubería.
                    int disponibles = entrada.available();
                    if (disponibles > 0) {
                        int leidos = entrada.read(bloque, 0, Math.min(disponibles, bloque.length));
                        if (leidos > maximoBytes - salida.size()) {
                            throw new SalidaExcedida();
                        }
                        if (leidos > 0) {
                            salida.write(bloque, 0, leidos);
                        }
                    } else if (termino) {
                        capturada = true;
                        return new Salida(proceso.exitValue(), salida.toString(StandardCharsets.UTF_8));
                    } else {
                        termino = proceso.waitFor(Math.min(restante, PAUSA_NANOS), TimeUnit.NANOSECONDS);
                    }
                }
            } finally {
                if (!capturada) {
                    detener(proceso);
                }
            }
        }
    }

    private static void detener(Process proceso) {
        // Captura los descendientes antes de enviar señales. Al morir el padre pueden dejar de aparecer en descendants().
        List<ProcessHandle> arbol = Stream.concat(proceso.descendants(), Stream.of(proceso.toHandle())).toList();
        CompletableFuture<Void> fin = CompletableFuture.allOf(arbol.stream()
                .map(ProcessHandle::onExit).toArray(CompletableFuture[]::new));
        arbol.forEach(ProcessHandle::destroy);
        esperarSalida(fin, 5);
        arbol.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        esperarSalida(fin, 1);
    }

    private static void esperarSalida(CompletableFuture<Void> fin, int segundos) {
        try {
            fin.get(segundos, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException ex) {
            // La espera de limpieza es acotada y después del cierre normal se fuerza a los que sigan vivos.
        }
    }

    record Salida(int codigo, String texto) {

        @Override
        public String toString() {
            return "Salida[codigo=%d]".formatted(codigo);
        }
    }

    static final class SalidaExcedida extends IOException {
    }
}
