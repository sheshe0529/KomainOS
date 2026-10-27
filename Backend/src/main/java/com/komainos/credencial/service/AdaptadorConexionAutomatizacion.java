package com.komainos.credencial.service;

import com.komainos.credencial.model.CredencialSecreto;
import com.komainos.credencial.model.CuentaServicio;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.repository.CuentaServicioRepositorio;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descifra la cuenta de servicio justo antes de conectarse (RNF02-RNF04). Solo lo usa el adaptador Ansible real; el
 * modo simulado nunca descifra secretos. Comprueba primero que la cuenta siga vigente y sirva para la familia.
 */
@Component
@RequiredArgsConstructor
public class AdaptadorConexionAutomatizacion implements PuertoConexionAutomatizacion {

    private final CuentaServicioRepositorio cuentas;
    private final CifradorCredenciales cifrador;
    private final AdaptadorCuentasServicio validacion;

    @Override
    @Transactional(readOnly = true)
    public Conexion resolver(Integer idCuenta, FamiliaSistemaOperativo familia) {
        validacion.exigirAsignable(idCuenta, familia);
        CuentaServicio cuenta = cuentas.findConSecretoById(idCuenta)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la cuenta de servicio", idCuenta));
        CredencialSecreto secreto = cuenta.secretoVigente()
                .orElseThrow(() -> new ReglaNegocioException("La cuenta de servicio %s no tiene un secreto vigente"
                        .formatted(cuenta.getNombre())));
        String claveSu = secreto.secretoSu().map(su -> cifrador.descifrarSu(su, idCuenta)).orElse(null);
        return new Conexion(cuenta.getUsuarioAcceso(), cifrador.descifrar(secreto.secreto(), idCuenta), claveSu,
                secreto.getTipoAutenticacion() == TipoAutenticacion.LLAVE_SSH);
    }
}
