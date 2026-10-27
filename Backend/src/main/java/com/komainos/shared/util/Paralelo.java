package com.komainos.shared.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Ejecuta un trabajo por elemento en hilos virtuales y espera a que terminen todos. Un fallo no interrumpe a los demás:
 * se devuelven las excepciones para que quien llama decida cómo registrarlas.
 */
public final class Paralelo {

    private Paralelo() {
    }

    public static <T> List<RuntimeException> paraCada(Collection<T> elementos, Consumer<T> trabajo) {
        List<RuntimeException> errores = new ArrayList<>();
        if (elementos.isEmpty()) {
            return errores;
        }
        try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> pendientes = new ArrayList<>();
            for (T elemento : elementos) {
                pendientes.add(hilos.submit(() -> trabajo.accept(elemento)));
            }
            for (Future<?> pendiente : pendientes) {
                try {
                    pendiente.get();
                } catch (ExecutionException ex) {
                    errores.add(ex.getCause() instanceof RuntimeException r ? r : new IllegalStateException(ex.getCause()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    errores.add(new IllegalStateException("Ejecución interrumpida", ex));
                }
            }
        }
        return errores;
    }
}
