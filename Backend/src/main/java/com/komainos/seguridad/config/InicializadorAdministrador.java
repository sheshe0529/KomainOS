package com.komainos.seguridad.config;

import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.model.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el primer administrador si la base no tiene ninguno activo (DEC-21).
 *
 * <p>Sin esta cuenta nadie podria iniciar sesion para registrar a los demas
 * usuarios. La contrasena sale del entorno y no tiene valor por defecto: una
 * clave conocida y versionada permitiria entrar a cualquier instalacion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InicializadorAdministrador implements ApplicationRunner {

    private final UsuarioRepositorio repositorio;
    private final ServicioUsuario servicioUsuario;
    private final PropiedadesAdministradorInicial propiedades;

    @Override
    public void run(ApplicationArguments args) {
        if (repositorio.existsByRolAndActivoTrue(Rol.ADMINISTRADOR)) {
            return;
        }
        if (!propiedades.tieneClave()) {
            log.warn("No existe ningún administrador activo y KOMAINOS_ADMIN_CLAVE_INICIAL no está definida: "
                    + "no se podrá iniciar sesión hasta definirla y reiniciar");
            return;
        }
        if (repositorio.existsByCodigoIgnoreCase(propiedades.codigo())) {
            log.warn("El código {} ya existe pero no es un administrador activo; no se crea el administrador inicial",
                    propiedades.codigo());
            return;
        }
        servicioUsuario.crear(propiedades.codigo(), propiedades.nombre(), propiedades.clave(),
                Rol.ADMINISTRADOR, Actor.sistema("INICIALIZACION"));
        log.info("Administrador inicial '{}' creado", propiedades.codigo());
    }
}
