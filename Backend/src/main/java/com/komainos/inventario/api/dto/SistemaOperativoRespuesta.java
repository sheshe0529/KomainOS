package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.FamiliaSistemaOperativo;
import com.komainos.inventario.dominio.SistemaOperativo;

import java.util.List;

public record SistemaOperativoRespuesta(Integer id, String nombre, FamiliaSistemaOperativo familia,
                                        String canalRemoto, boolean activo, List<Version> versiones) {

    public record Version(Integer id, String version, boolean activo) {
    }

    public static SistemaOperativoRespuesta de(SistemaOperativo so) {
        return new SistemaOperativoRespuesta(so.getId(), so.getNombre(), so.getFamilia(),
                so.getFamilia().canalRemoto(), so.isActivo(),
                so.getVersiones().stream()
                        .map(v -> new Version(v.getId(), v.getVersion(), v.isActivo()))
                        .toList());
    }
}
