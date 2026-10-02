package com.komainos.credencial.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.credencial.model.Credencial;
import com.komainos.credencial.model.CredencialDocumental;
import com.komainos.credencial.model.CredencialVersion;
import com.komainos.credencial.model.CuentaServicio;
import com.komainos.credencial.model.EstadoCredencial;
import com.komainos.credencial.model.SecretoCifrado;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.model.TipoUsuario;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Gestión de credenciales documentales y cuentas de servicio (RF04-RF08), el secreto solo sale en el revelado y la exportación autorizada */
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
        return documentales.findByServidorIdOrderByPrincipalDescEstadoAscNombreAsc(idServidor);
    }

    /** La primera credencial del servidor queda como principal (DEC-39) */
    @Transactional
    public CredencialDocumental registrarDocumental(Integer idServidor, DatosCredencial datos, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        servidor.exigirNoDadoDeBaja("registrar sus credenciales");
        Secreto acceso = completo(formaDocumental(datos.secreto(), servidor), null, null);
        return registrarDocumental(servidor, datos.nombre(), datos.usuarioAcceso(), datos.descripcion(), acceso,
                documentales.findByServidorIdAndPrincipalTrue(idServidor).isEmpty(), alcance.actor());
    }

    @Transactional
    public CredencialDocumental marcarPrincipal(Integer id, Actor actor) {
        CredencialDocumental credencial = documental(buscar(id));
        if (!credencial.isPrincipal()) {
            Integer idServidor = credencial.getServidor().getId();
            Optional<CredencialDocumental> anterior = documentales.findByServidorIdAndPrincipalTrue(idServidor);
            anterior.ifPresent(CredencialDocumental::quitarPrincipal);
            credencial.marcarPrincipal();
            auditoria.registrar(actor, operacion("MARCAR_CREDENCIAL_PRINCIPAL", credencial)
                    .valores(anterior.map(a -> Map.<String, Object>of("principal", a.getNombre())).orElse(null),
                            Map.of("principal", credencial.getNombre())));
        }
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
        Secreto acceso = completo(formaCuenta(datos.secreto(), null), null, null);

        CuentaServicio cuenta = cuentas.save(CuentaServicio.nueva(nombre, datos.usuarioAcceso().trim(),
                vacioANulo(datos.descripcion()), ahora()));
        CredencialVersion version = agregarVersion(cuenta, acceso);
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

    /** Crea una versión nueva y conserva las anteriores (RF04), un secreto vacío conserva el vigente si sigue aplicando */
    @Transactional
    public Credencial actualizarSecreto(Integer id, Secreto secreto, Actor actor) {
        Credencial credencial = buscar(id);
        credencial.exigirVigente("actualizar su secreto");
        Secreto forma = credencial instanceof CredencialDocumental documental
                ? formaDocumental(secreto, documental.getServidor())
                : formaCuenta(secreto, id);
        CredencialVersion actual = credencial.versionVigente().orElse(null);
        Secreto acceso = completo(forma, actual, id);

        Map<String, Object> anterior = instantanea(credencial, actual);
        CredencialVersion version = agregarVersion(credencial, acceso);
        auditoria.registrar(actor, operacion("ACTUALIZAR_SECRETO_CREDENCIAL", credencial)
                .valores(anterior, instantanea(credencial, version)));
        return credencial;
    }

    /** Una cuenta en uso no se revoca y una documental principal cede su lugar a la vigente más antigua */
    @Transactional
    public Credencial revocar(Integer id, String motivo, Actor actor) {
        Credencial credencial = buscar(id);
        if (credencial instanceof CuentaServicio cuenta) {
            exigirSinUso(cuenta);
        }
        credencial.revocar(ahora());
        Map<String, Object> detalle = new HashMap<>();
        if (credencial instanceof CredencialDocumental documental && documental.isPrincipal()) {
            documental.quitarPrincipal();
            siguientePrincipal(documental).ifPresent(siguiente -> {
                siguiente.marcarPrincipal();
                detalle.put("nuevaPrincipal", siguiente.getNombre());
            });
        }
        auditoria.registrar(actor, operacion("REVOCAR_CREDENCIAL", credencial)
                .valores(null, detalle.isEmpty() ? null : detalle)
                .motivo(vacioANulo(motivo)));
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
        AccesoClaro acceso = descifrar(credencial, version);
        auditoria.registrar(alcance.actor(), operacion("REVELAR_CREDENCIAL", credencial)
                .valores(null, Map.of("numeroVersion", version.getNumeroVersion(), "incluyeSu", acceso.su() != null)));
        return new Revelado(credencial, version, acceso.secreto(), acceso.su());
    }

    /** Con los secretos en claro: quien llama ya verificó la reautenticación del administrador */
    @Transactional(readOnly = true)
    public Map<Integer, AccesoExportado> principales(Collection<Integer> idsServidores) {
        Map<Integer, AccesoExportado> resultado = new HashMap<>();
        if (idsServidores.isEmpty()) {
            return resultado;
        }
        for (CredencialDocumental c : documentales.findByServidorIdInAndPrincipalTrue(idsServidores)) {
            c.versionVigente().ifPresent(v -> resultado.put(c.getServidor().getId(), exportado(c, v)));
        }
        return resultado;
    }

    /** Exportación adicional con todas las credenciales vigentes de todos los servidores, previa reautenticación */
    @Transactional
    public List<CredencialExportada> todasLasDocumentales(AlcanceUsuario alcance, String contrasena) {
        reautenticacion.exigir(alcance.usuarioId(), contrasena);
        List<CredencialExportada> filas = new ArrayList<>();
        for (CredencialDocumental c : documentales.findByEstadoOrderByServidorHostnameAscPrincipalDescNombreAsc(
                EstadoCredencial.VIGENTE)) {
            c.versionVigente().ifPresent(v -> filas.add(new CredencialExportada(c, v, exportado(c, v))));
        }
        auditoria.registrar(alcance.actor(), Operacion.de("EXPORTAR_CREDENCIALES", "credencial_documental", null)
                .valores(null, Map.of("registros", filas.size())));
        return filas;
    }

    /** Errores de forma de una credencial leída de un archivo, sin consultar la base */
    public List<String> validarImportada(Secreto secreto, String usuario, FamiliaSistemaOperativo familia) {
        List<String> errores = new ArrayList<>();
        if (usuario == null || usuario.isBlank()) {
            errores.add("Falta el usuario de acceso de la credencial");
        } else if (usuario.trim().length() > 255) {
            errores.add("El usuario de acceso de la credencial admite hasta 255 caracteres");
        }
        try {
            completo(formaDocumental(secreto, familia, "El servidor"), null, null);
        } catch (ReglaNegocioException ex) {
            errores.add(ex.getMessage());
        }
        return errores;
    }

    /** Falso si la principal registrada ya tiene ese usuario, mecanismo y secretos */
    @Transactional(readOnly = true)
    public boolean cambiaPrincipal(Integer idServidor, String usuario, Secreto secreto) {
        Optional<CredencialDocumental> principal = documentales.findByServidorIdAndPrincipalTrue(idServidor);
        if (principal.isEmpty() || !principal.get().getUsuarioAcceso().equalsIgnoreCase(usuario.trim())) {
            return true;
        }
        CredencialVersion version = principal.get().versionVigente().orElse(null);
        return version == null || !mismoAcceso(principal.get(), version, secreto);
    }

    /** El usuario identifica la credencial: si ya existe se le agrega una versión, si no se registra, y queda principal */
    @Transactional
    public CredencialDocumental aplicarPrincipalImportada(Integer idServidor, String usuario, Secreto secreto,
                                                          Actor actor) {
        Servidor servidor = servidores.obtenerSinAlcance(idServidor);
        Secreto acceso = completo(formaDocumental(secreto, servidor), null, null);
        String limpio = usuario.trim();
        Optional<CredencialDocumental> existente = documentales
                .findByServidorIdOrderByPrincipalDescEstadoAscNombreAsc(idServidor).stream()
                .filter(c -> c.estaVigente() && c.getUsuarioAcceso().equalsIgnoreCase(limpio))
                .findFirst();
        if (existente.isEmpty()) {
            documentales.findByServidorIdAndPrincipalTrue(idServidor).ifPresent(CredencialDocumental::quitarPrincipal);
            return registrarDocumental(servidor, limpio, limpio, null, acceso, true, actor);
        }
        CredencialDocumental credencial = existente.get();
        CredencialVersion actual = credencial.versionVigente().orElse(null);
        if (actual == null || !mismoAcceso(credencial, actual, acceso)) {
            Map<String, Object> anterior = instantanea(credencial, actual);
            CredencialVersion version = agregarVersion(credencial, acceso);
            auditoria.registrar(actor, operacion("ACTUALIZAR_SECRETO_CREDENCIAL", credencial)
                    .valores(anterior, instantanea(credencial, version))
                    .motivo("Importación del inventario"));
        }
        return marcarPrincipal(credencial.getId(), actor);
    }

    private CredencialDocumental registrarDocumental(Servidor servidor, String nombre, String usuario,
                                                     String descripcion, Secreto acceso, boolean principal,
                                                     Actor actor) {
        CredencialDocumental credencial = documentales.save(CredencialDocumental.nueva(servidor, nombre.trim(),
                usuario.trim(), vacioANulo(descripcion), principal, ahora()));
        CredencialVersion version = agregarVersion(credencial, acceso);
        auditoria.registrar(actor, operacion("REGISTRAR_CREDENCIAL", credencial)
                .valores(null, instantanea(credencial, version)));
        return credencial;
    }

    private Credencial buscar(Integer id) {
        return credenciales.findConVersionesById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la credencial", id));
    }

    private static CredencialDocumental documental(Credencial credencial) {
        if (credencial instanceof CredencialDocumental documental) {
            return documental;
        }
        throw new ReglaNegocioException("Solo las credenciales documentales de un servidor tienen principal");
    }

    private Optional<CredencialDocumental> siguientePrincipal(CredencialDocumental revocada) {
        return documentales.findByServidorIdOrderByPrincipalDescEstadoAscNombreAsc(revocada.getServidor().getId())
                .stream()
                .filter(c -> c.estaVigente() && !c.getId().equals(revocada.getId()))
                .min(Comparator.comparing(Credencial::getFechaRegistro).thenComparing(Credencial::getId));
    }

    /** El id ya existe porque la credencial se insertó antes: el cifrado lo usa como dato asociado */
    private CredencialVersion agregarVersion(Credencial credencial, Secreto acceso) {
        Integer id = credencial.getId();
        SecretoCifrado su = acceso.su() == null ? null : cifrador.cifrarSu(acceso.su(), id);
        return credencial.agregarVersion(acceso.tipo(), acceso.tipoUsuario(), cifrador.cifrar(acceso.valor(), id), su,
                ahora());
    }

    private AccesoClaro descifrar(Credencial credencial, CredencialVersion version) {
        Integer id = credencial.getId();
        return new AccesoClaro(cifrador.descifrar(version.secreto(), id),
                version.secretoSu().map(su -> cifrador.descifrarSu(su, id)).orElse(null));
    }

    private boolean mismoAcceso(Credencial credencial, CredencialVersion version, Secreto acceso) {
        if (version.getTipoAutenticacion() != acceso.tipo() || version.getTipoUsuario() != acceso.tipoUsuario()) {
            return false;
        }
        AccesoClaro actual = descifrar(credencial, version);
        return actual.secreto().equals(acceso.valor()) && Objects.equals(actual.su(), acceso.su());
    }

    private AccesoExportado exportado(CredencialDocumental c, CredencialVersion v) {
        AccesoClaro acceso = descifrar(c, v);
        return new AccesoExportado(v.getTipoAutenticacion(), v.getTipoUsuario(), c.getUsuarioAcceso(),
                acceso.secreto(), acceso.su());
    }

    private Secreto formaDocumental(Secreto secreto, Servidor servidor) {
        return formaDocumental(secreto, servidor.familia(), "El servidor " + servidor.getHostname());
    }

    /** Linux: contraseña o llave con usuario Administrador o Genérico (este con su), Windows: solo contraseña (DEC-39) */
    private static Secreto formaDocumental(Secreto s, FamiliaSistemaOperativo familia, String sujeto) {
        if (s.tipo() == null) {
            throw new ReglaNegocioException("Indique el mecanismo de acceso: contraseña o llave SSH");
        }
        if (familia == FamiliaSistemaOperativo.WINDOWS) {
            if (s.tipo() != TipoAutenticacion.PASSWORD) {
                throw new ReglaNegocioException("%s es Windows: WinRM solo admite usuario y contraseña".formatted(sujeto));
            }
            if (s.tipoUsuario() == TipoUsuario.GENERICO || tieneTexto(s.su())) {
                throw new ReglaNegocioException(
                        "%s es Windows: no lleva usuario Genérico ni contraseña su".formatted(sujeto));
            }
            return new Secreto(TipoAutenticacion.PASSWORD, null, s.valor(), null);
        }
        if (s.tipoUsuario() == null) {
            throw new ReglaNegocioException("Indique si el usuario es Administrador o Genérico");
        }
        if (s.tipoUsuario() == TipoUsuario.ADMINISTRADOR && tieneTexto(s.su())) {
            throw new ReglaNegocioException("Un usuario Administrador no lleva contraseña su");
        }
        return s.tipoUsuario() == TipoUsuario.ADMINISTRADOR ? new Secreto(s.tipo(), s.tipoUsuario(), s.valor(), null) : s;
    }

    /** Las familias que ya usan la cuenta limitan su mecanismo: WinRM solo admite contraseña */
    private Secreto formaCuenta(Secreto s, Integer idCuenta) {
        if (s.tipo() == null) {
            throw new ReglaNegocioException("Indique el mecanismo de acceso: contraseña o llave SSH");
        }
        if (s.tipoUsuario() != null || tieneTexto(s.su())) {
            throw new ReglaNegocioException("Las cuentas de servicio no llevan tipo de usuario ni contraseña su");
        }
        if (idCuenta != null && !s.tipo().admitidoEn(FamiliaSistemaOperativo.WINDOWS)
                && asignaciones.familiasQueUsan(idCuenta).contains(FamiliaSistemaOperativo.WINDOWS)) {
            throw new ReglaNegocioException("La cuenta la usan servidores Windows: WinRM solo admite usuario y contraseña");
        }
        return s;
    }

    /** Completa con la versión vigente lo que llega vacío y exige lo que falte */
    private Secreto completo(Secreto forma, CredencialVersion actual, Integer idCredencial) {
        String valor = forma.valor();
        if (!tieneTexto(valor) && actual != null && actual.getTipoAutenticacion() == forma.tipo()) {
            valor = cifrador.descifrar(actual.secreto(), idCredencial);
        }
        String su = forma.su();
        if (forma.tipoUsuario() == TipoUsuario.GENERICO && !tieneTexto(su) && actual != null && actual.tieneSu()) {
            su = cifrador.descifrarSu(actual.secretoSu().orElseThrow(), idCredencial);
        }
        if (!tieneTexto(valor)) {
            throw new ReglaNegocioException(forma.tipo() == TipoAutenticacion.LLAVE_SSH
                    ? "Ingrese la llave privada" : "Ingrese la contraseña");
        }
        if (forma.tipo() == TipoAutenticacion.LLAVE_SSH && !LLAVE_PRIVADA.matcher(valor).find()) {
            throw new ReglaNegocioException(
                    "La llave privada debe estar en formato PEM u OpenSSH (-----BEGIN ... PRIVATE KEY-----)");
        }
        if (forma.tipoUsuario() == TipoUsuario.GENERICO && !tieneTexto(su)) {
            throw new ReglaNegocioException("Un usuario Genérico requiere la contraseña su (root)");
        }
        return new Secreto(forma.tipo(), forma.tipoUsuario(), valor,
                forma.tipoUsuario() == TipoUsuario.GENERICO ? su : null);
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
        if (c instanceof CredencialDocumental documental) {
            v.put("principal", documental.isPrincipal());
        }
        if (version != null) {
            v.put("numeroVersion", version.getNumeroVersion());
            v.put("tipoAutenticacion", version.getTipoAutenticacion().name());
            v.put("tipoUsuario", version.getTipoUsuario() == null ? null : version.getTipoUsuario().name());
            v.put("conSu", version.tieneSu());
        }
        return v;
    }

    private Instant ahora() {
        return Tiempo.ahora(reloj);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** valor y su nulos al actualizar: se conservan los vigentes si siguen aplicando */
    public record Secreto(TipoAutenticacion tipo, TipoUsuario tipoUsuario, String valor, String su) {

        public Secreto(TipoAutenticacion tipo, String valor) {
            this(tipo, null, valor, null);
        }
    }

    public record DatosCredencial(String nombre, String usuarioAcceso, String descripcion, Secreto secreto) {
    }

    public record CuentaConUso(CuentaServicio cuenta, long servidores, long grupos, boolean predeterminada) {
    }

    public record Revelado(Credencial credencial, CredencialVersion version, String secreto, String su) {
    }

    public record AccesoExportado(TipoAutenticacion tipo, TipoUsuario tipoUsuario, String usuario, String secreto,
                                  String su) {
    }

    public record CredencialExportada(CredencialDocumental credencial, CredencialVersion version,
                                      AccesoExportado acceso) {
    }

    private record AccesoClaro(String secreto, String su) {
    }
}
