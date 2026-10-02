package com.komainos.credencial.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.credencial.model.Credencial;
import com.komainos.credencial.model.CredencialDocumental;
import com.komainos.credencial.model.CredencialVersion;
import com.komainos.credencial.model.CuentaServicio;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.repository.CredencialDocumentalRepositorio;
import com.komainos.credencial.repository.CredencialRepositorio;
import com.komainos.credencial.repository.CuentaServicioRepositorio;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.ServicioAsignacionCuentas.UsosCuentas;
import com.komainos.inventario.service.ServicioAsignacionCuentas;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.service.ServicioReautenticacion;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Gestión de credenciales documentales y cuentas de servicio (RF04-RF08), el secreto nunca sale salvo en el revelado */
@Service
@RequiredArgsConstructor
public class ServicioCredenciales {

    private static final Pattern LLAVE_PRIVADA =
            Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.+-----END [A-Z0-9 ]*PRIVATE KEY-----", Pattern.DOTALL);

    private final CredencialRepositorio credenciales;
    private final CredencialDocumentalRepositorio documentales;
    private final CuentaServicioRepositorio cuentas;
    private final CifradorCredenciales cifrador;
    private final ServicioServidor servidores;
    private final ServicioAsignacionCuentas asignaciones;
    private final ServicioReautenticacion reautenticacion;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public List<CredencialDocumental> documentalesDe(Integer idServidor, AlcanceUsuario alcance) {
        servidores.obtener(idServidor, alcance);
        return documentales.findByServidorIdOrderByEstadoAscNombreAsc(idServidor);
    }

    @Transactional
    public CredencialDocumental registrarDocumental(Integer idServidor, DatosCredencial datos, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        servidor.exigirNoDadoDeBaja("registrar sus credenciales");
        exigirMecanismo(datos.secreto(), servidor.familia(), "El servidor %s es Windows".formatted(servidor.getHostname()));

        CredencialDocumental credencial = documentales.save(CredencialDocumental.nueva(servidor,
                datos.nombre().trim(), datos.usuarioAcceso().trim(), vacioANulo(datos.descripcion()), ahora()));
        CredencialVersion version = agregarVersion(credencial, datos.secreto());
        auditoria.registrar(alcance.actor(), operacion("REGISTRAR_CREDENCIAL", credencial)
                .valores(null, instantanea(credencial, version)));
        return credencial;
    }

    @Transactional(readOnly = true)
    public List<CuentaConUso> cuentasDeServicio() {
        UsosCuentas usos = asignaciones.usos();
        return cuentas.findAllByOrderByEstadoAscNombreAsc().stream()
                .map(c -> new CuentaConUso(c, usos.servidoresDe(c.getId()), usos.gruposDe(c.getId()),
                        usos.esPredeterminada(c.getId())))
                .toList();
    }

    @Transactional
    public CuentaServicio registrarCuenta(DatosCredencial datos, Actor actor) {
        String nombre = datos.nombre().trim();
        if (cuentas.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("Ya existe una cuenta de servicio con el nombre %s".formatted(nombre));
        }
        exigirMecanismo(datos.secreto(), null, null);

        CuentaServicio cuenta = cuentas.save(CuentaServicio.nueva(nombre, datos.usuarioAcceso().trim(),
                vacioANulo(datos.descripcion()), ahora()));
        CredencialVersion version = agregarVersion(cuenta, datos.secreto());
        auditoria.registrar(actor, operacion("REGISTRAR_CREDENCIAL", cuenta).valores(null, instantanea(cuenta, version)));
        return cuenta;
    }

    @Transactional
    public Credencial actualizarDatos(Integer id, String nombre, String usuarioAcceso, String descripcion, Actor actor) {
        Credencial credencial = buscar(id);
        String limpio = nombre.trim();
        if (credencial instanceof CuentaServicio && cuentas.existsByNombreIgnoreCaseAndIdNot(limpio, id)) {
            throw new ConflictoException("Ya existe otra cuenta de servicio con el nombre %s".formatted(limpio));
        }
        CredencialVersion version = credencial.versionVigente().orElse(null);
        Map<String, Object> anterior = instantanea(credencial, version);
        credencial.actualizarDatos(limpio, usuarioAcceso.trim(), vacioANulo(descripcion));
        auditoria.registrar(actor, operacion("ACTUALIZAR_CREDENCIAL", credencial)
                .valores(anterior, instantanea(credencial, version)));
        return credencial;
    }

    /** Crea una versión nueva: las anteriores se conservan para el uso histórico (RF04) */
    @Transactional
    public Credencial actualizarSecreto(Integer id, Secreto secreto, Actor actor) {
        Credencial credencial = buscar(id);
        credencial.exigirVigente("actualizar su secreto");
        if (credencial instanceof CredencialDocumental documental) {
            Servidor servidor = documental.getServidor();
            exigirMecanismo(secreto, servidor.familia(), "El servidor %s es Windows".formatted(servidor.getHostname()));
        } else {
            exigirMecanismo(secreto, null, null);
            for (FamiliaSistemaOperativo familia : asignaciones.familiasQueUsan(id)) {
                exigirMecanismo(secreto, familia,
                        "La cuenta %s la usan servidores Windows".formatted(credencial.getNombre()));
            }
        }
        Map<String, Object> anterior = instantanea(credencial, credencial.versionVigente().orElse(null));
        CredencialVersion version = agregarVersion(credencial, secreto);
        auditoria.registrar(actor, operacion("ACTUALIZAR_SECRETO_CREDENCIAL", credencial)
                .valores(anterior, instantanea(credencial, version)));
        return credencial;
    }

    /** Una cuenta en uso no se revoca: las configuraciones quedarían apuntando a una cuenta inutilizable */
    @Transactional
    public Credencial revocar(Integer id, String motivo, Actor actor) {
        Credencial credencial = buscar(id);
        if (credencial instanceof CuentaServicio cuenta) {
            exigirSinUso(cuenta);
        }
        credencial.revocar(ahora());
        auditoria.registrar(actor, operacion("REVOCAR_CREDENCIAL", credencial).motivo(vacioANulo(motivo)));
        return credencial;
    }

    /** RF08: exige reautenticación y deja constancia en la auditoría, sin el secreto (RNF06, RNF11) */
    @Transactional
    public Revelado revelar(Integer id, String contrasena, AlcanceUsuario alcance) {
        reautenticacion.exigir(alcance.usuarioId(), contrasena);
        Credencial credencial = buscar(id);
        credencial.exigirVigente("revelar su secreto");
        CredencialVersion version = credencial.versionVigente()
                .orElseThrow(() -> new ReglaNegocioException("La credencial %s no tiene un secreto registrado"
                        .formatted(credencial.getNombre())));
        String secreto = cifrador.descifrar(version.secreto(), credencial.getId());
        auditoria.registrar(alcance.actor(), operacion("REVELAR_CREDENCIAL", credencial)
                .valores(null, Map.of("numeroVersion", version.getNumeroVersion())));
        return new Revelado(credencial, version, secreto);
    }

    private Credencial buscar(Integer id) {
        return credenciales.findConVersionesById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la credencial", id));
    }

    /** El id ya existe porque la credencial se insertó antes: el cifrado lo usa como dato asociado */
    private CredencialVersion agregarVersion(Credencial credencial, Secreto secreto) {
        return credencial.agregarVersion(secreto.tipo(), cifrador.cifrar(secreto.valor(), credencial.getId()), ahora());
    }

    private void exigirSinUso(CuentaServicio cuenta) {
        UsosCuentas usos = asignaciones.usos();
        List<String> enUso = new ArrayList<>();
        long servidoresConCuenta = usos.servidoresDe(cuenta.getId());
        long gruposConCuenta = usos.gruposDe(cuenta.getId());
        if (servidoresConCuenta > 0) {
            enUso.add("%d servidor(es)".formatted(servidoresConCuenta));
        }
        if (gruposConCuenta > 0) {
            enUso.add("%d grupo(s)".formatted(gruposConCuenta));
        }
        if (usos.esPredeterminada(cuenta.getId())) {
            enUso.add("es la cuenta predeterminada del sistema");
        }
        if (!enUso.isEmpty()) {
            throw new ReglaNegocioException("La cuenta de servicio %s está en uso (%s): reasígnela antes de revocarla"
                    .formatted(cuenta.getNombre(), String.join(", ", enUso)));
        }
    }

    private static void exigirMecanismo(Secreto secreto, FamiliaSistemaOperativo familia, String sujeto) {
        if (!secreto.tipo().admitidoEn(familia)) {
            throw new ReglaNegocioException("%s: WinRM solo admite usuario y contraseña".formatted(sujeto));
        }
        if (secreto.tipo() == TipoAutenticacion.LLAVE_SSH && !LLAVE_PRIVADA.matcher(secreto.valor()).find()) {
            throw new ReglaNegocioException(
                    "La llave privada debe estar en formato PEM u OpenSSH (-----BEGIN ... PRIVATE KEY-----)");
        }
    }

    private static Operacion operacion(String nombre, Credencial credencial) {
        if (credencial instanceof CredencialDocumental documental) {
            return Operacion.de(nombre, "credencial_documental", credencial.getId())
                    .sobreServidor(documental.getServidor().getId());
        }
        return Operacion.de(nombre, "cuenta_servicio", credencial.getId());
    }

    /** Nunca incluye el secreto ni su forma cifrada (RNF11) */
    private static Map<String, Object> instantanea(Credencial c, CredencialVersion version) {
        Map<String, Object> v = new HashMap<>();
        v.put("nombre", c.getNombre());
        v.put("usuarioAcceso", c.getUsuarioAcceso());
        v.put("descripcion", c.getDescripcion());
        v.put("estado", c.getEstado().name());
        if (version != null) {
            v.put("numeroVersion", version.getNumeroVersion());
            v.put("tipoAutenticacion", version.getTipoAutenticacion().name());
        }
        return v;
    }

    private Instant ahora() {
        return Tiempo.ahora(reloj);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public record Secreto(TipoAutenticacion tipo, String valor) {
    }

    public record DatosCredencial(String nombre, String usuarioAcceso, String descripcion, Secreto secreto) {
    }

    public record CuentaConUso(CuentaServicio cuenta, long servidores, long grupos, boolean predeterminada) {
    }

    public record Revelado(Credencial credencial, CredencialVersion version, String secreto) {
    }
}
