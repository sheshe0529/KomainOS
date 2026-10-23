package com.komainos.inventario.dto;

import com.komainos.inventario.model.OrigenUmbral;
import com.komainos.shared.dto.ReferenciaSimple;

import java.util.List;

/** Umbral que rige para el servidor como mantenimiento individual, de dónde sale y cuál es el global */
public record UmbralServidorRespuesta(
        ReferenciaSimple umbral,
        MetricaRespuesta metrica,
        OrigenUmbral origen,
        /** Nula si se aplica el umbral global */
        ExcepcionAplicadaRespuesta excepcion,
        ValoresUmbralRespuesta aplicado,
        ValoresUmbralRespuesta global,
        List<UmbralEnGrupoRespuesta> enGrupos) {
}
