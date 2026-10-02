package com.komainos.inventario.dto;

import com.komainos.inventario.model.DatosServidor;
import com.komainos.shared.validation.DireccionIp;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** El estado no se edita aquí: cambia con la configuración, la baja o la reactivación */
public record ServidorPeticion(

        @NotBlank(message = "El hostname es obligatorio")
        @Size(max = 255, message = "El hostname no puede superar los 255 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$",
                message = "El hostname solo admite letras, números, puntos, guiones y guiones bajos")
        String hostname,

        /** IP principal */
        @NotBlank(message = "La dirección IP principal es obligatoria")
        @Size(max = 45, message = "La dirección IP no puede superar los 45 caracteres")
        @DireccionIp
        String direccionIp,

        @Size(max = 20, message = "Un servidor puede tener hasta 20 direcciones IP adicionales")
        List<@NotBlank(message = "La dirección IP adicional no puede estar vacía")
             @Size(max = 45, message = "La dirección IP no puede superar los 45 caracteres")
             @DireccionIp String> direccionesIpAdicionales,

        @Size(max = 255, message = "El VDC no puede superar los 255 caracteres")
        String vdc,

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
        String descripcion,

        @Positive(message = "La cantidad de CPU debe ser mayor que 0")
        @Max(value = 4096, message = "La cantidad de CPU no puede superar 4096")
        Integer cantidadCpu,

        @Positive(message = "La RAM debe ser mayor que 0")
        @Digits(integer = 5, fraction = 2, message = "La RAM admite hasta 5 enteros y 2 decimales (GB)")
        BigDecimal ramGb,

        @Positive(message = "El disco virtual debe ser mayor que 0")
        @Digits(integer = 8, fraction = 2, message = "El disco virtual admite hasta 8 enteros y 2 decimales (GB)")
        BigDecimal hdVirtualGb) {

    public DatosServidor aDatos() {
        return new DatosServidor(hostname, direccionIp, vdc, servidorFisico, vlan, cluster, dns,
                idVersionSistemaOperativo, plataforma, idEntorno, idNivelCriticidad, idResponsable, descripcion,
                direccionesIpAdicionales, cantidadCpu, ramGb, hdVirtualGb);
    }
}
