package com.komainos.inventario.dominio;

/**
 * Familia del sistema operativo ({@code enum_familia_so}). Determina el canal
 * remoto de ejecucion sin agentes (RNF09).
 */
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
