package com.komainos.shared.model;

/** Un usuario o un proceso del Sistema: la auditoría exige exactamente uno (ck_auditoria_ejecutor) */
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
