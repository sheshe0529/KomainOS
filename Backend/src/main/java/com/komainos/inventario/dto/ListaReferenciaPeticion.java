package com.komainos.inventario.dto;

import com.komainos.inventario.model.TipoDato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ListaReferenciaPeticion(
        @NotBlank(message = "El nombre de la lista es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        /** No cambia una vez creada la lista: al actualizar debe ser el mismo */
        @NotNull(message = "Indique el tipo de dato de los elementos")
        TipoDato tipoDato,

        /** Solo en listas de texto */
        boolean ignoraMayusculas,

        @NotEmpty(message = "La lista debe tener al menos un elemento")
        @Size(max = 500, message = "La lista admite hasta 500 elementos")
        List<@NotNull(message = "Los elementos de la lista no pueden estar vacíos")
             @Size(max = 255, message = "Cada elemento admite hasta 255 caracteres") String> elementos,

        /** Al actualizar, la fechaActualizacion que leyó el formulario: si otro la cambió después, responde 409 */
        Instant versionEsperada) {
}
