package com.komainos.inventario.dto;

import com.komainos.inventario.model.DatosServidor;
import com.komainos.shared.validation.DireccionIp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta y edicion de un servidor (RF09, RF10). La edicion es un reemplazo
 * completo (PUT). El estado no se edita por aqui: cambia con la configuracion,
 * la baja o la reactivacion, que tienen sus propias reglas.
 *
 * <p>Los limites de longitud repiten los de la tabla {@code servidor} para que
 * un valor demasiado largo responda 400 indicando el campo, y no un error de
 * base de datos.
 */
public record ServidorPeticion(

        @NotBlank(message = "El hostname es obligatorio")
        @Size(max = 255, message = "El hostname no puede superar los 255 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$",
                message = "El hostname solo admite letras, números, puntos, guiones y guiones bajos")
        String hostname,

        @NotBlank(message = "La dirección IP es obligatoria")
        @Size(max = 45, message = "La dirección IP no puede superar los 45 caracteres")
        @DireccionIp
        String direccionIp,

        @Size(max = 255, message = "El datacenter no puede superar los 255 caracteres")
        String datacenter,

        @Size(max = 255, message = "El servidor físico no puede superar los 255 caracteres")
        String servidorFisico,

        @Size(max = 100, message = "La VLAN no puede superar los 100 caracteres")
        String vlan,

        @Size(max = 255, message = "El clúster no puede superar los 255 caracteres")
        String cluster,

        @Size(max = 255, message = "El DNS no puede superar los 255 caracteres")
        String dns,

        @NotNull(message = "La versión del sistema operativo es obligatoria")
        Integer idVersionSistemaOperativo,

        @Size(max = 255, message = "La plataforma no puede superar los 255 caracteres")
        String plataforma,

        @NotNull(message = "El entorno es obligatorio")
        Integer idEntorno,

        @NotNull(message = "El nivel de criticidad es obligatorio")
        Integer idNivelCriticidad,

        @NotNull(message = "El responsable es obligatorio")
        Integer idResponsable,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion) {

    public DatosServidor aDatos() {
        return new DatosServidor(hostname, direccionIp, datacenter, servidorFisico, vlan, cluster, dns,
                idVersionSistemaOperativo, plataforma, idEntorno, idNivelCriticidad, idResponsable, descripcion);
    }
}
