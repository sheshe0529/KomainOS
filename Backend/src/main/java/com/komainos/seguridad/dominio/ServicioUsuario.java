package com.komainos.seguridad.dominio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.seguridad.infra.EspecificacionesUsuario;
import com.komainos.seguridad.infra.UsuarioRepositorio;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.error.ConflictoException;
import com.komainos.shared.error.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Cuentas de usuario (RF03). En esta iteracion es una dependencia del
 * inventario: todo servidor exige un responsable (DEC-15).
 */
@Service
@RequiredArgsConstructor
public class ServicioUsuario {

    private final UsuarioRepositorio repositorio;
    private final PasswordEncoder codificador;
    private final ServicioAuditoria auditoria;

    @Transactional(readOnly = true)
    public Page<Usuario> listar(FiltroUsuarios filtro, Pageable paginacion) {
        return repositorio.findAll(EspecificacionesUsuario.con(filtro), paginacion);
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Integer id) {
        return repositorio.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", id));
    }

    /** RF03: impide codigos de usuario duplicados. */
    @Transactional
    public Usuario crear(String codigo, String nombreCompleto, String contrasena, Rol rol, Actor actor) {
        String codigoNormalizado = codigo.trim();
        if (repositorio.existsByCodigoIgnoreCase(codigoNormalizado)) {
            throw new ConflictoException("Ya existe un usuario con el código %s".formatted(codigoNormalizado));
        }
        Usuario usuario = repositorio.save(Usuario.nuevo(
                codigoNormalizado, nombreCompleto.trim(), codificador.encode(contrasena), rol));
        auditoria.registrar(actor, Operacion.de("CREAR_USUARIO", "usuario", usuario.getId())
                .valores(null, instantanea(usuario)));
        return usuario;
    }

    @Transactional
    public Usuario actualizar(Integer id, String nombreCompleto, Rol rol, Actor actor) {
        Usuario usuario = obtener(id);
        Map<String, Object> anterior = instantanea(usuario);
        usuario.setNombreCompleto(nombreCompleto.trim());
        usuario.setRol(rol);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_USUARIO", "usuario", id)
                .valores(anterior, instantanea(usuario)));
        return usuario;
    }

    @Transactional
    public Usuario activar(Integer id, Actor actor) {
        Usuario usuario = obtener(id);
        usuario.activar();
        auditoria.registrar(actor, Operacion.de("ACTIVAR_USUARIO", "usuario", id));
        return usuario;
    }

    /** RF03: la cuenta se desactiva, no se borra, y conserva su historial. */
    @Transactional
    public Usuario desactivar(Integer id, Actor actor) {
        Usuario usuario = obtener(id);
        usuario.desactivar();
        auditoria.registrar(actor, Operacion.de("DESACTIVAR_USUARIO", "usuario", id));
        return usuario;
    }

    /** Nunca incluye el hash de la contrasena (RNF11). */
    private static Map<String, Object> instantanea(Usuario u) {
        return Map.of("codigo", u.getCodigo(), "nombreCompleto", u.getNombreCompleto(),
                "rol", u.getRol().name(), "activo", u.isActivo());
    }
}
