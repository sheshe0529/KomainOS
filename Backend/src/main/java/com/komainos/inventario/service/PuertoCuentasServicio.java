package com.komainos.inventario.service;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.util.Optional;

/** Cuentas de servicio vistas desde la configuración: la implementación vive en credencial, que depende del inventario (DEC-38) */
public interface PuertoCuentasServicio {

    /** Existe, está vigente y su mecanismo sirve para la familia indicada, nula admite cualquiera */
    void exigirAsignable(Integer idCuenta, FamiliaSistemaOperativo familia);

    Optional<String> nombre(Integer idCuenta);
}
