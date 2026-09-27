package com.komainos.inventario.dominio.intercambio;

import java.util.List;

/** Resultado de una importación confirmada (RF12, HU08 CA3). */
public record ResultadoImportacion(List<Fila> filas) {

    public enum Resultado {
        CREADO,
        ACTUALIZADO,
        /** Duplicado que no se sobrescribió: el usuario no lo confirmó o no era posible. */
        OMITIDO,
        RECHAZADO
    }

    /** @param detalle motivo del rechazo u omisión; nulo si se aplicó */
    public record Fila(int numero, String hostname, Resultado resultado, Integer idServidor, String detalle) {
    }

    public long contar(Resultado resultado) {
        return filas.stream().filter(f -> f.resultado() == resultado).count();
    }
}
