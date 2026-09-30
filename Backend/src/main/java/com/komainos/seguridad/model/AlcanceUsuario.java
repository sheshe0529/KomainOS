package com.komainos.seguridad.model;

import com.komainos.shared.model.Actor;

/**
 * Identidad y alcance del solicitante, en terminos de dominio.
 *
 * <p>Permite a los servicios aplicar "el responsable solo ve sus servidores"
 * (RF11, RF32) sin importar nada de Spring Security. El controlador traduce el
 * principal autenticado a este valor; de ahi hacia adentro el alcance es una
 * regla de negocio mas, comprobable con un objeto literal en una prueba.
 */
public record AlcanceUsuario(Integer usuarioId, Rol rol) {

    public static AlcanceUsuario de(UsuarioAutenticado autenticado) {
        return new AlcanceUsuario(autenticado.id(), autenticado.rol());
    }

    /** Administrador y operador ven todo el inventario; el responsable no. */
    public boolean veTodoElInventario() {
        return rol != Rol.RESPONSABLE;
    }

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    public Actor actor() {
        return Actor.usuario(usuarioId);
    }
}
