package com.komainos.inventario.dto;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OperadorComparacion;

import java.math.BigDecimal;

/** Lo que rige al evaluar: operador y valores en una métrica numérica; lista y niveles en una de lista */
public record ValoresUmbralRespuesta(
        OperadorComparacion operador,
        BigDecimal valorAdvertencia,
        BigDecimal valorCritico,
        ListaResumenRespuesta lista,
        NivelAlerta nivelFaltante,
        NivelAlerta nivelSobrante) {
}
