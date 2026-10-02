package com.komainos.inventario.service.intercambio;

import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.shared.util.archivo.FormatoArchivo;

import java.util.List;

public record AnalisisImportacion(FormatoArchivo formato,
                                  List<ColumnaInventario> columnasReconocidas,
                                  List<ColumnaCredencial> columnasCredencial,
                                  List<String> columnasIgnoradas,
                                  List<Fila> filas) {

    public enum EstadoFila {
        NUEVA,
        DUPLICADA,
        ERRONEA
    }

    /** credencial: la principal que trae el registro, nunca se devuelve en la vista previa */
    public record Fila(int numero, EstadoFila estado, String hostname, String direccionIp,
                       List<String> motivos, Servidor existente, boolean sobrescribible,
                       List<String> camposModificados, DatosServidor datos, CredencialArchivo credencial) {

        public boolean cambiaServidor() {
            return camposModificados.stream().anyMatch(c -> !c.equals(ColumnaCredencial.CAMBIO));
        }

        public boolean cambiaCredencial() {
            return camposModificados.contains(ColumnaCredencial.CAMBIO);
        }
    }

    public long contar(EstadoFila estado) {
        return filas.stream().filter(f -> f.estado() == estado).count();
    }

    public long sobrescribibles() {
        return filas.stream().filter(Fila::sobrescribible).count();
    }
}
