package com.komainos.credencial.service;

import com.komainos.credencial.model.Credencial;
import com.komainos.credencial.model.CredencialVersion;
import com.komainos.credencial.model.CuentaServicio;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.repository.CuentaServicioRepositorio;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Separado de ServicioCredenciales: este depende del inventario y el inventario depende de este puerto */
@Component
@RequiredArgsConstructor
public class AdaptadorCuentasServicio implements PuertoCuentasServicio {

    private final CuentaServicioRepositorio cuentas;

    @Override
    @Transactional(readOnly = true)
    public void exigirAsignable(Integer idCuenta, FamiliaSistemaOperativo familia) {
        CuentaServicio cuenta = cuentas.findConVersionesById(idCuenta)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la cuenta de servicio", idCuenta));
        if (!cuenta.estaVigente()) {
            throw new ReglaNegocioException("La cuenta de servicio %s está revocada".formatted(cuenta.getNombre()));
        }
        TipoAutenticacion tipo = cuenta.versionVigente().map(CredencialVersion::getTipoAutenticacion)
                .orElse(TipoAutenticacion.PASSWORD);
        if (!tipo.admitidoEn(familia)) {
            throw new ReglaNegocioException(("La cuenta de servicio %s usa llave privada SSH: los servidores Windows "
                    + "se conectan por WinRM con usuario y contraseña").formatted(cuenta.getNombre()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nombre(Integer idCuenta) {
        return cuentas.findById(idCuenta).map(Credencial::getNombre);
    }
}
