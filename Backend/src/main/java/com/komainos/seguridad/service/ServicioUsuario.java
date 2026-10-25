package com.komainos.seguridad.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.seguridad.config.CodificadorContrasena;
import com.komainos.seguridad.model.FiltroUsuarios;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.EspecificacionesUsuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Concurrencia;
import com.komainos.shared.validation.ValidadorMaximoBytes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioUsuario {

    private final UsuarioRepositorio repositorio;
    private final PasswordEncoder codificador;
    private final ServicioAuditoria auditoria;
    private final RegistroRevocaciones revocaciones;

    @Transactional(readOnly = true)
    public Page<Usuario> listar(FiltroUsuarios filtro, Pageable paginacion) {
        return repositorio.findAll(EspecificacionesUsuario.con(filtro), paginacion);
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Integer id) {
        return repositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", id));
    }

    @Transactional
    public Usuario crear(String codigo, String nombreCompleto, String contrasena, Rol rol, Actor actor) {
        String codigoNormalizado = codigo.trim();
        if (ValidadorMaximoBytes.bytes(contrasena) > CodificadorContrasena.MAXIMO_BYTES) {
            throw new ReglaNegocioException("La contraseña no puede superar los %d bytes: cada ñ o vocal con tilde ocupa dos"
                    .formatted(CodificadorContrasena.MAXIMO_BYTES));
        }
        if (repositorio.existsByCodigoIgnoreCase(codigoNormalizado)) {
            throw new ConflictoException("Ya existe un usuario con el código %s".formatted(codigoNormalizado));
        }
        Usuario usuario = repositorio.save(Usuario.nuevo(
                codigoNormalizado, nombreCompleto.trim(), codificador.encode(contrasena), rol));
        auditoria.registrar(actor, Operacion.de("CREAR_USUARIO", "usuario", usuario.getId())
                .valores(null, instantanea(usuario)));
        return usuario;
    }

    /** Un cambio de rol cierra las sesiones abiertas de la cuenta: el token emitido con el rol anterior deja de valer */
    @Transactional
    public Usuario actualizar(Integer id, String nombreCompleto, Rol rol, Instant versionEsperada, Actor actor) {
        Usuario usuario = obtener(id);
        Concurrencia.exigirVersion(versionEsperada, usuario.getFechaActualizacion(), "el usuario " + usuario.getCodigo());
        boolean cambiaRol = usuario.getRol() != rol;
        if (cambiaRol && usuario.getRol() == Rol.ADMINISTRADOR) {
            exigirOtroAdministrador(usuario, actor, "cambiar el rol de su propia cuenta");
        }
        Map<String, Object> anterior = instantanea(usuario);
        usuario.setNombreCompleto(nombreCompleto.trim());
        usuario.setRol(rol);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_USUARIO", "usuario", id)
                .valores(anterior, instantanea(usuario)));
        if (cambiaRol) {
            revocaciones.revocarSesionesDe(usuario.getCodigo());
        }
        return usuario;
    }

    @Transactional
    public Usuario actualizar(Integer id, String nombreCompleto, Rol rol, Actor actor) {
        return actualizar(id, nombreCompleto, rol, null, actor);
    }

    @Transactional
    public Usuario activar(Integer id, Actor actor) {
        Usuario usuario = obtener(id);
        usuario.activar();
        auditoria.registrar(actor, Operacion.de("ACTIVAR_USUARIO", "usuario", id));
        return usuario;
    }

    /** La cuenta se desactiva, no se borra, y conserva su historial (RF03); sus sesiones abiertas dejan de valer */
    @Transactional
    public Usuario desactivar(Integer id, Actor actor) {
        Usuario usuario = obtener(id);
        if (usuario.getRol() == Rol.ADMINISTRADOR && usuario.isActivo()) {
            exigirOtroAdministrador(usuario, actor, "desactivar su propia cuenta");
        }
        usuario.desactivar();
        auditoria.registrar(actor, Operacion.de("DESACTIVAR_USUARIO", "usuario", id));
        revocaciones.revocarSesionesDe(usuario.getCodigo());
        return usuario;
    }

    /**
     * Siempre queda al menos un administrador activo, y ninguno se degrada o desactiva a sí mismo: lo hace otro
     * administrador. Los administradores activos se bloquean para que dos cambios simultáneos no los dejen en cero.
     */
    private void exigirOtroAdministrador(Usuario usuario, Actor actor, String accion) {
        if (!actor.esSistema() && usuario.getId().equals(actor.usuarioId())) {
            throw new ReglaNegocioException("No puede %s: pídalo a otro administrador".formatted(accion));
        }
        boolean quedaOtro = repositorio.bloquearActivosConRol(Rol.ADMINISTRADOR).stream()
                .anyMatch(u -> !u.getId().equals(usuario.getId()));
        if (!quedaOtro) {
            throw new ReglaNegocioException("%s es el único administrador activo: el sistema debe conservar al menos uno"
                    .formatted(usuario.getCodigo()));
        }
    }

    /** Nunca incluye el hash de la contraseña (RNF11) */
    private static Map<String, Object> instantanea(Usuario u) {
        return Map.of("codigo", u.getCodigo(), "nombreCompleto", u.getNombreCompleto(),
                "rol", u.getRol().name(), "activo", u.isActivo());
    }
}
