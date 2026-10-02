package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales.DatosCredencial;
import com.komainos.credencial.service.ServicioCredenciales.Secreto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de una credencial con su primer secreto (RF04, HU03) */
public record CredencialPeticion(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre admite hasta 255 caracteres")
        String nombre,

        @NotBlank(message = "El usuario de acceso es obligatorio")
        @Size(max = 255, message = "El usuario de acceso admite hasta 255 caracteres")
        String usuarioAcceso,

        @Size(max = 500, message = "La descripción admite hasta 500 caracteres")
        String descripcion,

        @NotNull(message = "El mecanismo de autenticación es obligatorio")
        TipoAutenticacion tipoAutenticacion,

        /** No se recorta: un espacio puede ser parte de la contraseña */
        @NotBlank(message = "El secreto es obligatorio")
        @Size(max = SecretoPeticion.MAXIMO, message = "El secreto admite hasta 16384 caracteres")
        String secreto) {

    public DatosCredencial aDatos() {
        return new DatosCredencial(nombre, usuarioAcceso, descripcion, new Secreto(tipoAutenticacion, secreto));
    }
}
