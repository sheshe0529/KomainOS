package com.komainos.catalogo.dto;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.List;import com.komainos.inventario.model.TipoDato;
public record VersionScriptPeticion(@NotBlank @Size(max=131072)String contenido,@Size(max=1000)String notasCambio,@NotNull @Size(max=50)List<@NotNull @Valid ParametroVersionPeticion> parametros){
 public record ParametroVersionPeticion(@NotBlank @Pattern(regexp="[A-Za-z_][A-Za-z0-9_]{0,63}")String nombre,@NotNull TipoDato tipo,boolean obligatorio,@Size(max=1000)String valorPorDefecto){}
}
