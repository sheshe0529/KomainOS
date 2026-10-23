package com.komainos.inventario.dto;

import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.service.ServicioListasReferencia.ListaConUso;

import java.time.Instant;
import java.util.List;

public record ListaReferenciaRespuesta(
        Integer id,
        String nombre,
        TipoDato tipoDato,
        boolean ignoraMayusculas,
        boolean activo,
        /** Vigentes: los retirados se conservan con su fecha de baja para explicar evaluaciones pasadas */
        List<String> elementos,
        /** Umbrales que la tienen como lista general */
        long umbrales,
        /** Excepciones de umbral que la usan */
        long excepciones,
        Instant fechaActualizacion) {

    public static ListaReferenciaRespuesta de(ListaConUso uso) {
        ListaReferencia l = uso.lista();
        return new ListaReferenciaRespuesta(l.getId(), l.getNombre(), l.getTipoDato(), l.isIgnoraMayusculas(),
                l.isActivo(), l.elementosVigentes(), uso.umbrales(), uso.excepciones(), l.getFechaActualizacion());
    }
}
