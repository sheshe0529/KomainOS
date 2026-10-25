package com.komainos.catalogo.dto;
import jakarta.validation.constraints.*;import java.util.Set;import com.komainos.catalogo.model.NivelAfectacion;
public record TareaPeticion(@NotNull Integer idScript,@NotBlank @Size(max=255)String nombre,@Size(max=500)String descripcion,@NotNull NivelAfectacion nivelAfectacion,@Min(0)int prioridad,@Min(1)int duracionEstimadaMinutos,boolean activo,@NotNull @Size(max=1000)Set<@NotNull Integer> idsUmbrales){}
