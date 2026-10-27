package com.komainos.catalogo.dto;

import com.komainos.catalogo.model.TipoScript;
import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.time.Instant;
import java.util.List;

/** Fila del catálogo: compatibilidad y versión vigente de cada implementación, sin el código */
public record ScriptResumenRespuesta(Integer id, String nombre, TipoScript tipo, String descripcion, boolean activo,
                                     List<ImplementacionResumen> implementaciones) {

    /**
     * @param versionVigente la última habilitada, que se fija en una MOP nueva; nula si todas están deshabilitadas
     */
    public record ImplementacionResumen(Integer id, Integer idVersionSistemaOperativo, String sistemaOperativo,
                                        FamiliaSistemaOperativo familia, Integer versionVigente, int totalVersiones,
                                        Instant fechaUltimaVersion) {
    }
}
