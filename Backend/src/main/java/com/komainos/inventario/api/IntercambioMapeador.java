package com.komainos.inventario.api;

import com.komainos.inventario.api.dto.AnalisisImportacionRespuesta;
import com.komainos.inventario.api.dto.ColumnaInventarioRespuesta;
import com.komainos.inventario.api.dto.FilaAnalisisRespuesta;
import com.komainos.inventario.api.dto.FilaImportacionRespuesta;
import com.komainos.inventario.api.dto.ResultadoImportacionRespuesta;
import com.komainos.inventario.dominio.intercambio.AnalisisImportacion;
import com.komainos.inventario.dominio.intercambio.AnalisisImportacion.EstadoFila;
import com.komainos.inventario.dominio.intercambio.ColumnaInventario;
import com.komainos.inventario.dominio.intercambio.ResultadoImportacion;
import com.komainos.inventario.dominio.intercambio.ResultadoImportacion.Resultado;
import com.komainos.shared.api.ReferenciaSimple;

/** Traduce la importación y exportación del inventario a DTO (RF12, RF13). */
final class IntercambioMapeador {

    private IntercambioMapeador() {
    }

    static ColumnaInventarioRespuesta columna(ColumnaInventario c) {
        return new ColumnaInventarioRespuesta(c.clave(), c.etiqueta(), c.importable(), c.obligatoria());
    }

    static AnalisisImportacionRespuesta analisis(AnalisisImportacion a) {
        return new AnalisisImportacionRespuesta(a.formato().name(), a.filas().size(),
                a.contar(EstadoFila.NUEVA), a.contar(EstadoFila.DUPLICADA), a.contar(EstadoFila.ERRONEA),
                a.sobrescribibles(),
                a.columnasReconocidas().stream().map(ColumnaInventario::etiqueta).toList(),
                a.columnasIgnoradas(),
                a.filas().stream().map(IntercambioMapeador::fila).toList());
    }

    private static FilaAnalisisRespuesta fila(AnalisisImportacion.Fila f) {
        ReferenciaSimple existente = f.existente() == null ? null
                : new ReferenciaSimple(f.existente().getId(), f.existente().getHostname());
        return new FilaAnalisisRespuesta(f.numero(), f.estado(), f.hostname(), f.direccionIp(), f.motivos(),
                existente, f.sobrescribible(), f.camposModificados());
    }

    static ResultadoImportacionRespuesta resultado(ResultadoImportacion r) {
        return new ResultadoImportacionRespuesta(r.contar(Resultado.CREADO), r.contar(Resultado.ACTUALIZADO),
                r.contar(Resultado.OMITIDO), r.contar(Resultado.RECHAZADO),
                r.filas().stream()
                        .map(f -> new FilaImportacionRespuesta(f.numero(), f.hostname(), f.resultado(),
                                f.idServidor(), f.detalle()))
                        .toList());
    }
}
