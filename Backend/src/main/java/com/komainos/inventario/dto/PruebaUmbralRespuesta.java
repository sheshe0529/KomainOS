package com.komainos.inventario.dto;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OrigenUmbral;

import java.math.BigDecimal;
import java.util.List;

/** Resultado de probar una medición contra lo que rige para el servidor; no se registra */
public record PruebaUmbralRespuesta(
        NivelAlerta nivel,
        OrigenUmbral origen,
        ExcepcionAplicadaRespuesta excepcion,
        ValoresUmbralRespuesta aplicado,
        /** Solo en métricas numéricas */
        BigDecimal valor,
        /** Solo en métricas de lista: esperados que no se informaron */
        List<String> faltantes,
        /** Solo en métricas de lista: informados que no se esperaban */
        List<String> sobrantes) {
}
