package com.komainos.credencial.mapper;

import com.komainos.credencial.dto.CredencialRespuesta;
import com.komainos.credencial.dto.CuentaServicioRespuesta;
import com.komainos.credencial.dto.SecretoReveladoRespuesta;
import com.komainos.credencial.dto.ServidorAsignableRespuesta;
import com.komainos.credencial.model.Credencial;
import com.komainos.credencial.model.CredencialDocumental;
import com.komainos.credencial.model.CredencialVersion;
import com.komainos.credencial.service.ServicioCredenciales.CredencialExportada;
import com.komainos.credencial.service.ServicioCredenciales.CuentaConUso;
import com.komainos.credencial.service.ServicioCredenciales.Revelado;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.intercambio.ColumnaCredencial;
import com.komainos.shared.util.archivo.ColumnaArchivo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Espera las versiones ya cargadas por el servicio: open-in-view está desactivado */
public final class CredencialMapeador {

    private static final ColumnaArchivo HOSTNAME = new ColumnaArchivo("hostname", "Hostname");
    private static final ColumnaArchivo DIRECCION_IP = new ColumnaArchivo("direccion_ip", "Dirección IP");
    private static final ColumnaArchivo NOMBRE = new ColumnaArchivo("credencial", "Credencial");
    private static final ColumnaArchivo PRINCIPAL = new ColumnaArchivo("principal", "Principal");
    private static final ColumnaArchivo VERSION = new ColumnaArchivo("version_del_secreto", "Versión del secreto");
    private static final ColumnaArchivo FECHA = new ColumnaArchivo("fecha_del_secreto", "Fecha del secreto");
    private static final ColumnaArchivo DESCRIPCION = new ColumnaArchivo("descripcion", "Descripción");

    /** Mismas columnas de credencial que el inventario, precedidas por el servidor y la credencial */
    public static final List<ColumnaArchivo> COLUMNAS_EXPORTACION = columnasExportacion();

    private CredencialMapeador() {
    }

    private static List<ColumnaArchivo> columnasExportacion() {
        List<ColumnaArchivo> columnas = new ArrayList<>(List.of(HOSTNAME, DIRECCION_IP, NOMBRE, PRINCIPAL));
        for (ColumnaCredencial c : ColumnaCredencial.values()) {
            columnas.add(c.comoColumnaArchivo());
        }
        columnas.addAll(List.of(VERSION, FECHA, DESCRIPCION));
        return List.copyOf(columnas);
    }

    public static Map<String, ?> filaExportacion(CredencialExportada e) {
        Map<String, Object> fila = new HashMap<>();
        CredencialDocumental c = e.credencial();
        fila.put(HOSTNAME.clave(), c.getServidor().getHostname());
        fila.put(DIRECCION_IP.clave(), c.getServidor().getDireccionIp());
        fila.put(NOMBRE.clave(), c.getNombre());
        fila.put(PRINCIPAL.clave(), c.isPrincipal() ? "Sí" : "No");
        fila.put(ColumnaCredencial.MECANISMO.clave(), e.acceso().tipo().etiqueta());
        fila.put(ColumnaCredencial.TIPO_USUARIO.clave(),
                e.acceso().tipoUsuario() == null ? null : e.acceso().tipoUsuario().etiqueta());
        fila.put(ColumnaCredencial.USUARIO.clave(), e.acceso().usuario());
        fila.put(ColumnaCredencial.SECRETO.clave(), e.acceso().secreto());
        fila.put(ColumnaCredencial.SECRETO_SU.clave(), e.acceso().su());
        fila.put(VERSION.clave(), e.version().getNumeroVersion());
        fila.put(FECHA.clave(), e.version().getFechaCreacion());
        fila.put(DESCRIPCION.clave(), c.getDescripcion());
        return fila;
    }

    public static CredencialRespuesta credencial(Credencial c) {
        Optional<CredencialVersion> vigente = c.versionVigente();
        return new CredencialRespuesta(c.getId(), c.getNombre(), c.getUsuarioAcceso(), c.getDescripcion(),
                c.getEstado(),
                vigente.map(CredencialVersion::getTipoAutenticacion).orElse(null),
                vigente.map(CredencialVersion::getTipoUsuario).orElse(null),
                vigente.map(CredencialVersion::tieneSu).orElse(false),
                c instanceof CredencialDocumental d ? d.isPrincipal() : null,
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
                r.credencial().getUsuarioAcceso(), r.version().getTipoAutenticacion(), r.version().getTipoUsuario(),
                r.version().getNumeroVersion(), r.secreto(), r.su(), segundosVisible);
    }

    public static ServidorAsignableRespuesta asignable(ConfiguracionServidor c) {
        Servidor s = c.getServidor();
        return new ServidorAsignableRespuesta(s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(),
                c.getIdCuentaServicio());
    }
}
