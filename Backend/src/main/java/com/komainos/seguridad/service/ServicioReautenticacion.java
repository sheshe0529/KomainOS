package com.komainos.seguridad.service;

import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF08: quien ya inició sesión vuelve a ingresar su contraseña antes de ver o exportar un secreto */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioReautenticacion {

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;

    /** 422 y no 401: un 401 haría que el panel cierre la sesión del usuario */
    @Transactional(readOnly = true)
    public void exigir(Integer idUsuario, String contrasena) {
        Usuario usuario = usuarios.findById(idUsuario).orElse(null);
        boolean valida = usuario != null && usuario.isActivo() && contrasena != null
                && codificador.matches(contrasena, usuario.getHashContrasena());
        if (!valida) {
            log.warn("Reautenticación rechazada para el usuario {}", idUsuario);
            throw new ReglaNegocioException("La contraseña no es correcta");
        }
    }
}
