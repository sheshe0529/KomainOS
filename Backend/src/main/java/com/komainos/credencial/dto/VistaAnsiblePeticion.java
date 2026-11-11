package com.komainos.credencial.dto;

import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lo que el formulario de la cuenta ya sabe, sin secretos: basta para mostrar la configuración de Ansible (DEC-57) */
public record VistaAnsiblePeticion(
        @Size(max = 255, message = "El usuario de acceso admite hasta 255 caracteres")
        String usuarioAcceso,

        @NotNull(message = "El mecanismo de autenticación es obligatorio")
        TipoAutenticacion tipoAutenticacion,

        MetodoElevacion metodoElevacion,

        Boolean elevacionConContrasena,

        /** Solo esa familia; sin ella, todas en las que la cuenta puede usarse */
        FamiliaSistemaOperativo familia) {
}
