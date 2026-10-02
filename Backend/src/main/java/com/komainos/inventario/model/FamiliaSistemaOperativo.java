package com.komainos.inventario.model;

public enum FamiliaSistemaOperativo {

    LINUX("SSH"),
    WINDOWS("WinRM");

    private final String canalRemoto;

    FamiliaSistemaOperativo(String canalRemoto) {
        this.canalRemoto = canalRemoto;
    }

    public String canalRemoto() {
        return canalRemoto;
    }
}
