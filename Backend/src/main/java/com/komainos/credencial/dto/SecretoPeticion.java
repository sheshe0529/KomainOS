package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales.Secreto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nueva versión del secreto, el mecanismo puede cambiar entre versiones (RF04) */
public record SecretoPeticion(
        @NotNull(message = "El mecanismo de autenticación es obligatorio")
        TipoAutenticacion tipoAutenticacion,

        @NotBlank(message = "El secreto es obligatorio")
        @Size(max = SecretoPeticion.MAXIMO, message = "El secreto admite hasta 16384 caracteres")
        String secreto) {

    /** Una llave RSA de 4096 bits en PEM ocupa unos 3300 caracteres */
    public static final int MAXIMO = 16384;

    public Secreto aSecreto() {
        return new Secreto(tipoAutenticacion, secreto);
    }
}
