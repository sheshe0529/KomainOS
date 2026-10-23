package com.komainos.inventario.dto;

import com.komainos.shared.dto.ReferenciaSimple;

/** Lo que aplicaría al servidor dentro de una orden de ese grupo, que tiene su propia excepción */
public record UmbralEnGrupoRespuesta(ReferenciaSimple grupo, ExcepcionAplicadaRespuesta excepcion,
                                     ValoresUmbralRespuesta valores) {
}
