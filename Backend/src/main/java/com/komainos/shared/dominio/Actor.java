package com.komainos.shared.dominio;

/**
 * Quien ejecuta una operacion: un usuario o un proceso automatico del Sistema.
 *
 * <p>La especificacion trata al Sistema como un actor mas (R2.1, clases de
 * usuario), y la tabla auditoria exige exactamente uno de los dos
 * (ck_auditoria_ejecutor). Pasarlo explicito a los servicios evita que el
 * dominio tenga que leer el contexto de Spring Security, y permite que un
 * proceso programado reutilice el mismo caso de uso que un usuario.
 */
public record Actor(Integer usuarioId, String proceso) {

    public Actor {
        boolean esUsuario = usuarioId != null;
        boolean esProceso = proceso != null && !proceso.isBlank();
        if (esUsuario == esProceso) {
            throw new IllegalArgumentException("Un actor es un usuario o un proceso, no ambos ni ninguno");
        }
    }

    public static Actor usuario(Integer usuarioId) {
        return new Actor(usuarioId, null);
    }

    public static Actor sistema(String proceso) {
        return new Actor(null, proceso);
    }

    public boolean esSistema() {
        return usuarioId == null;
    }
}
