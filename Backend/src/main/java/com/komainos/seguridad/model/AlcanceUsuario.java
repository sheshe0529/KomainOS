package com.komainos.seguridad.model;

import com.komainos.shared.model.Actor;

/** El controlador traduce el principal a este valor: los servicios aplican el alcance sin depender de Spring Security (RF11) */
public record AlcanceUsuario(Integer usuarioId, Rol rol) {

    public static AlcanceUsuario de(UsuarioAutenticado autenticado) {
        return new AlcanceUsuario(autenticado.id(), autenticado.rol());
    }

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
