package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.model.TipoUsuario;
import com.komainos.credencial.service.ServicioCredenciales.Secreto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nueva versión del secreto: un secreto vacío conserva el vigente si sigue aplicando (RF04, DEC-39) */
public record SecretoPeticion(
        @NotNull(message = "El mecanismo de autenticación es obligatorio")
        TipoAutenticacion tipoAutenticacion,

        TipoUsuario tipoUsuario,

        @Size(max = SecretoPeticion.MAXIMO, message = "El secreto admite hasta 16384 caracteres")
        String secreto,

        @Size(max = 1024, message = "La contraseña su admite hasta 1024 caracteres")
        String secretoSu) {

    /** Una llave RSA de 4096 bits en PEM ocupa unos 3300 caracteres */
    public static final int MAXIMO = 16384;

    public Secreto aSecreto() {
        return new Secreto(tipoAutenticacion, tipoUsuario, secreto, secretoSu);
    }
}
