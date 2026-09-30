package com.komainos.inventario.service.intercambio;

import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.Servidor;
import com.komainos.shared.util.archivo.FormatoArchivo;

import java.util.List;

/**
 * Vista previa de una importación (RF12, HU08 CA2): cada registro del archivo
 * clasificado antes de confirmar.
 *
 * @param columnasIgnoradas encabezados que no se importan (desconocidos o
 *                          administrados por el sistema, como el estado)
 */
public record AnalisisImportacion(FormatoArchivo formato,
                                  List<ColumnaInventario> columnasReconocidas,
                                  List<String> columnasIgnoradas,
                                  List<Fila> filas) {

    public enum EstadoFila {
        /** Se registrará como servidor nuevo. */
        NUEVA,
        /** Coincide con un servidor registrado o con otra fila del archivo. */
        DUPLICADA,
        /** No se puede importar: datos faltantes, inválidos o referencias inexistentes. */
        ERRONEA
    }

    /**
     * @param existente         servidor registrado con el que coincide, si lo hay
     * @param sobrescribible    si el usuario puede confirmar que se reemplacen los
     *                          datos del existente (HU08 CA4)
     * @param camposModificados etiquetas de los datos que cambiarían al sobrescribir
     * @param datos             datos listos para registrar; nulos si la fila es errónea
     */
    public record Fila(int numero, EstadoFila estado, String hostname, String direccionIp,
                       List<String> motivos, Servidor existente, boolean sobrescribible,
                       List<String> camposModificados, DatosServidor datos) {
    }

    public long contar(EstadoFila estado) {
        return filas.stream().filter(f -> f.estado() == estado).count();
    }

    public long sobrescribibles() {
        return filas.stream().filter(Fila::sobrescribible).count();
    }
}
