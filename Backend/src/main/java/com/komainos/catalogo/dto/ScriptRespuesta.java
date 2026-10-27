package com.komainos.catalogo.dto;

import com.komainos.catalogo.model.TipoScript;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.TipoDato;

import java.time.Instant;
import java.util.List;

/** Script con todas sus implementaciones y versiones, incluido el código de las anteriores (RF26, HU17 CA5) */
public record ScriptRespuesta(Integer id, String nombre, TipoScript tipo, String descripcion, boolean activo,
                              long tareasActivas, List<ImplementacionRespuesta> implementaciones) {

    /** @param lenguaje bash o powershell, para el editor */
    public record ImplementacionRespuesta(Integer id, Integer idVersionSistemaOperativo, String sistemaOperativo,
                                          FamiliaSistemaOperativo familia, String lenguaje, Integer versionVigente,
                                          List<VersionRespuesta> versiones) {
    }

    /** @param vigente es la que se fija en una MOP nueva (RF42) */
    public record VersionRespuesta(Integer id, int numero, String contenido, String notasCambio, boolean habilitada,
                                   boolean vigente, Instant fechaCreacion, List<ParametroRespuesta> parametros) {
    }

    public record ParametroRespuesta(String nombre, TipoDato tipo, boolean obligatorio, String valorPorDefecto) {
    }
}
