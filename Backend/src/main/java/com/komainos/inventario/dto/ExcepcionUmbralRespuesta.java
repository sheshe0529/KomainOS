package com.komainos.inventario.dto;

import com.komainos.inventario.model.AlcanceExcepcion;
import com.komainos.shared.dto.ReferenciaSimple;

import java.math.BigDecimal;
import java.time.Instant;

public record ExcepcionUmbralRespuesta(
        Integer id,
        AlcanceExcepcion alcance,
        /** Servidor (hostname), grupo, entorno o nivel de criticidad */
        ReferenciaSimple objetivo,
        BigDecimal valorAdvertencia,
        BigDecimal valorCritico,
        ListaResumenRespuesta lista,
        String motivo,
        boolean activo,
        Instant fechaActualizacion) {
}
