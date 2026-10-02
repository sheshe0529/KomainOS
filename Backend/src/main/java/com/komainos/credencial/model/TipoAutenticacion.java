package com.komainos.credencial.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

public enum TipoAutenticacion {
    PASSWORD("Contraseña"),
    LLAVE_SSH("Llave SSH");

    private final String etiqueta;

    TipoAutenticacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Como se escribe en los archivos de intercambio */
    public String etiqueta() {
        return etiqueta;
    }

    /** WinRM solo admite usuario y contraseña (HU04 CA3), familia nula admite cualquiera */
    public boolean admitidoEn(FamiliaSistemaOperativo familia) {
        return familia != FamiliaSistemaOperativo.WINDOWS || this == PASSWORD;
    }
}
