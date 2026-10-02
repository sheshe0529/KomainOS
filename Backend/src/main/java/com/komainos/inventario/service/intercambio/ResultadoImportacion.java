package com.komainos.inventario.service.intercambio;

import java.util.List;

public record ResultadoImportacion(List<Fila> filas) {

    public enum Resultado {
        CREADO,
        ACTUALIZADO,
        OMITIDO,
        RECHAZADO
    }

    public record Fila(int numero, String hostname, Resultado resultado, Integer idServidor, String detalle) {
    }

    public long contar(Resultado resultado) {
        return filas.stream().filter(f -> f.resultado() == resultado).count();
    }
}
