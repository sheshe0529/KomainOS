package com.komainos.seguridad.api.dto;

public record TokenRespuesta(
        String token,
        String tipo,
        long expiraEnMinutos,
        UsuarioSesion usuario) {

    public static TokenRespuesta de(String token, long expiraEnMinutos, UsuarioSesion usuario) {
        return new TokenRespuesta(token, "Bearer", expiraEnMinutos, usuario);
    }
}
