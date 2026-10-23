package com.komainos.inventario.dto;

import com.komainos.inventario.model.AlcanceExcepcion;
import com.komainos.shared.dto.ReferenciaSimple;

public record ExcepcionAplicadaRespuesta(Integer id, AlcanceExcepcion alcance, ReferenciaSimple objetivo, String motivo) {
}
