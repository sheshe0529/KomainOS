package com.komainos.credencial.mapper;

import com.komainos.credencial.dto.CredencialRespuesta;
import com.komainos.credencial.dto.CuentaServicioRespuesta;
import com.komainos.credencial.dto.SecretoReveladoRespuesta;
import com.komainos.credencial.dto.ServidorAsignableRespuesta;
import com.komainos.credencial.model.Credencial;
import com.komainos.credencial.model.CredencialVersion;
import com.komainos.credencial.service.ServicioCredenciales.CuentaConUso;
import com.komainos.credencial.service.ServicioCredenciales.Revelado;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.Servidor;

import java.util.Optional;

/** Espera las versiones ya cargadas por el servicio: open-in-view está desactivado */
public final class CredencialMapeador {

    private CredencialMapeador() {
    }

    public static CredencialRespuesta credencial(Credencial c) {
        Optional<CredencialVersion> vigente = c.versionVigente();
        return new CredencialRespuesta(c.getId(), c.getNombre(), c.getUsuarioAcceso(), c.getDescripcion(),
                c.getEstado(),
                vigente.map(CredencialVersion::getTipoAutenticacion).orElse(null),
                vigente.map(CredencialVersion::getNumeroVersion).orElse(null),
                c.getFechaRegistro(),
                vigente.map(CredencialVersion::getFechaCreacion).orElse(null),
                c.getFechaRevocacion());
    }

    public static CuentaServicioRespuesta cuenta(CuentaConUso uso) {
        CredencialRespuesta c = credencial(uso.cuenta());
        return new CuentaServicioRespuesta(c.id(), c.nombre(), c.usuarioAcceso(), c.descripcion(), c.estado(),
                c.tipoAutenticacion(), c.numeroVersion(), c.fechaRegistro(), c.fechaSecreto(), c.fechaRevocacion(),
                uso.servidores(), uso.grupos(), uso.predeterminada());
    }

    public static SecretoReveladoRespuesta revelado(Revelado r, int segundosVisible) {
        return new SecretoReveladoRespuesta(r.credencial().getId(), r.credencial().getNombre(),
                r.credencial().getUsuarioAcceso(), r.version().getTipoAutenticacion(), r.version().getNumeroVersion(),
                r.secreto(), segundosVisible);
    }

    public static ServidorAsignableRespuesta asignable(ConfiguracionServidor c) {
        Servidor s = c.getServidor();
        return new ServidorAsignableRespuesta(s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(),
                c.getIdCuentaServicio());
    }
}
