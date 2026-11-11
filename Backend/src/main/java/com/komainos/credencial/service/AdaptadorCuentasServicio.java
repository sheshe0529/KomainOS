package com.komainos.credencial.service;

import com.komainos.credencial.model.Credencial;
import com.komainos.credencial.model.CredencialSecreto;
import com.komainos.credencial.model.CuentaServicio;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.repository.CuentaServicioRepositorio;
import com.komainos.ejecucion.model.MetodoElevacion;
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

    /**
     * Es una comprobación: quien la usa para decidir (por ejemplo, la preparación de un servidor) captura la excepción,
     * y su transacción no debe quedar marcada para deshacerse por ella.
     */
    @Override
    @Transactional(readOnly = true, noRollbackFor = {ReglaNegocioException.class, RecursoNoEncontradoException.class})
    public void exigirAsignable(Integer idCuenta, FamiliaSistemaOperativo familia) {
        CuentaServicio cuenta = cuentas.findConSecretoById(idCuenta)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la cuenta de servicio", idCuenta));
        if (!cuenta.estaVigente()) {
            throw new ReglaNegocioException("La cuenta de servicio %s está revocada".formatted(cuenta.getNombre()));
        }
        TipoAutenticacion tipo = cuenta.secretoVigente().map(CredencialSecreto::getTipoAutenticacion)
                .orElse(TipoAutenticacion.PASSWORD);
        if (!tipo.admitidoEn(familia)) {
            throw new ReglaNegocioException(("La cuenta de servicio %s usa llave privada SSH: los servidores Windows "
                    + "se conectan por WinRM con usuario y contraseña").formatted(cuenta.getNombre()));
        }
        MetodoElevacion metodo = cuenta.secretoVigente().map(CredencialSecreto::getMetodoElevacion)
                .orElse(MetodoElevacion.NINGUNO);
        if (!metodo.admitidoEn(familia)) {
            throw new ReglaNegocioException("La cuenta de servicio %s eleva con %s, que es solo para servidores %s"
                    .formatted(cuenta.getNombre(), metodo.etiqueta(),
                            metodo.familia() == FamiliaSistemaOperativo.WINDOWS ? "Windows" : "Linux"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nombre(Integer idCuenta) {
        return cuentas.findById(idCuenta).map(Credencial::getNombre);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumenCuenta> resumen(Integer idCuenta) {
        return cuentas.findConSecretoById(idCuenta).map(c -> {
            Optional<CredencialSecreto> secreto = c.secretoVigente();
            return new ResumenCuenta(c.getNombre(), c.getUsuarioAcceso(),
                    secreto.map(s -> s.getTipoAutenticacion().etiqueta()).orElse(null),
                    secreto.map(s -> s.getMetodoElevacion().etiqueta()).orElse(MetodoElevacion.NINGUNO.etiqueta()),
                    secreto.map(CredencialSecreto::tieneContrasenaElevacion).orElse(false));
        });
    }
}
