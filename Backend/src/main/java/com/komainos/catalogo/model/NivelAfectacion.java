package com.komainos.catalogo.model;
public enum NivelAfectacion { SIN_AFECTACION, PARCIAL, TOTAL;
 public NivelAfectacion mayor(NivelAfectacion otro) { return ordinal() >= otro.ordinal() ? this : otro; }
}
