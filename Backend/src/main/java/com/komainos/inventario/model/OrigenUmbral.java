package com.komainos.inventario.model;

/** De dónde salen los valores aplicados a un servidor: una excepción de cada alcance o el umbral general */
public enum OrigenUmbral {

    SERVIDOR,
    GRUPO,
    ENTORNO,
    CRITICIDAD,
    GLOBAL
}
