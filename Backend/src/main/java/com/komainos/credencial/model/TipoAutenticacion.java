package com.komainos.credencial.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

public enum TipoAutenticacion {
    PASSWORD,
    LLAVE_SSH;

    /** WinRM solo admite usuario y contraseña (HU04 CA3), familia nula admite cualquiera */
    public boolean admitidoEn(FamiliaSistemaOperativo familia) {
        return familia != FamiliaSistemaOperativo.WINDOWS || this == PASSWORD;
    }
}
