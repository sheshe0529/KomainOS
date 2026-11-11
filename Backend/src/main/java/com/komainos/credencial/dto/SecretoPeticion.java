package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales.Secreto;
import com.komainos.ejecucion.model.MetodoElevacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Nuevo secreto: un campo vacío conserva el vigente si sigue aplicando (RF04, DEC-39, DEC-57) */
public record SecretoPeticion(
        @NotNull(message = "El mecanismo de autenticación es obligatorio")
        TipoAutenticacion tipoAutenticacion,

        /** Omitirlo en una cuenta de servicio conserva la elevación vigente con su contraseña */
        MetodoElevacion metodoElevacion,

        @Size(max = SecretoPeticion.MAXIMO, message = "El secreto admite hasta 16384 caracteres")
        String secreto,

        /** Con sudo: si pide contraseña. Con su siempre la pide; ninguno y runas no la llevan */
        Boolean elevacionConContrasena,

        /** Vacía con el mismo método conserva la contraseña de elevación vigente */
        @Size(max = 1024, message = "La contraseña de elevación admite hasta 1024 caracteres")
        String secretoElevacion) {

    /** Una llave RSA de 4096 bits en PEM ocupa unos 3300 caracteres */
    public static final int MAXIMO = 16384;

    public Secreto aSecreto() {
        return new Secreto(tipoAutenticacion, metodoElevacion, secreto, secretoElevacion, elevacionConContrasena);
    }

    @Override
    public String toString() {
        return "SecretoPeticion[tipoAutenticacion=%s, metodoElevacion=%s]".formatted(tipoAutenticacion, metodoElevacion);
    }
}
