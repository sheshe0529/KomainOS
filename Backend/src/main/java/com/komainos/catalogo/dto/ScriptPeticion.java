package com.komainos.catalogo.dto;
import jakarta.validation.constraints.*;import com.komainos.catalogo.model.TipoScript;
public record ScriptPeticion(@NotBlank @Size(max=255) String nombre,@NotNull TipoScript tipo,@Size(max=500) String descripcion,boolean activo){}
