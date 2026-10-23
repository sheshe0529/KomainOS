package com.komainos.inventario.dto;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OperadorComparacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Valor global del umbral; en el detalle, además, sus excepciones por orden de prioridad */
public record UmbralRespuesta(
        Integer id,
        String nombre,
        MetricaRespuesta metrica,
        boolean activo,
        OperadorComparacion operador,
        BigDecimal valorAdvertencia,
        BigDecimal valorCritico,
        ListaResumenRespuesta lista,
        NivelAlerta nivelFaltante,
        NivelAlerta nivelSobrante,
        int cantidadExcepciones,
        int excepcionesActivas,
        /** Solo en el detalle */
        List<ExcepcionUmbralRespuesta> excepciones,
        Instant fechaActualizacion) {
}
