package com.komainos.inventario.service;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.model.Actor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Credencial principal en el intercambio del inventario: la implementación vive en credencial (DEC-39) */
public interface PuertoCredencialesInventario {

    /** Con los secretos en claro, solo para la exportación autorizada con reautenticación (RF13) */
    Map<Integer, CredencialArchivo> principales(Collection<Integer> idsServidores);

    /** Errores de forma para un servidor de esa familia, sin consultar la base */
    List<String> validar(CredencialArchivo credencial, FamiliaSistemaOperativo familia);

    /** Verdadero si aplicarla cambiaría la credencial principal registrada */
    boolean cambiaPrincipal(Integer idServidor, FamiliaSistemaOperativo familia, CredencialArchivo credencial);

    void aplicarPrincipal(Integer idServidor, FamiliaSistemaOperativo familia, CredencialArchivo credencial, Actor actor);
}
