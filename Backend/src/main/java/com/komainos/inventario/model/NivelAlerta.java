package com.komainos.inventario.model;

/** Nivel alcanzado por una medición frente a un umbral (enum_nivel_alerta); el orden de declaración es su gravedad */
public enum NivelAlerta {

    NORMAL,
    ADVERTENCIA,
    CRITICO;

    public NivelAlerta peor(NivelAlerta otro) {
        return otro != null && otro.ordinal() > ordinal() ? otro : this;
    }
}
