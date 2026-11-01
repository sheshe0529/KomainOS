package com.komainos.catalogo.dto;

import com.komainos.catalogo.model.TipoScript;
import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.time.Instant;
import java.util.List;

/**
 * Fila del catálogo: compatibilidad y versión vigente de cada implementación, sin el código
 *
 * @param creadoPor quien registró el script según la auditoría; nulo si se cargó fuera del sistema
 */
public record ScriptResumenRespuesta(Integer id, String nombre, TipoScript tipo, String descripcion, boolean activo,
                                     String creadoPor, List<ImplementacionResumen> implementaciones) {

    /**
     * @param versionVigente la última habilitada, que se fija en una MOP nueva; nula si todas están deshabilitadas
     */
    public record ImplementacionResumen(Integer id, Integer idVersionSistemaOperativo, String sistemaOperativo,
                                        FamiliaSistemaOperativo familia, Integer versionVigente, int totalVersiones,
                                        Instant fechaUltimaVersion) {
    }
}
