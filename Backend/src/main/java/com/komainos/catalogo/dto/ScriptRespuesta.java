package com.komainos.catalogo.dto;
import com.komainos.catalogo.model.*;import com.komainos.inventario.model.TipoDato;import java.time.Instant;import java.util.List;
public record ScriptRespuesta(Integer id,String nombre,TipoScript tipo,String descripcion,boolean activo,List<ImplementacionScriptVista> implementaciones){
 public static ScriptRespuesta de(Script s){return new ScriptRespuesta(s.getId(),s.getNombre(),s.getTipo(),s.getDescripcion(),s.isActivo(),s.getImplementaciones().stream().map(ImplementacionScriptVista::de).toList());}
 public record ImplementacionScriptVista(Integer id,Integer idVersionSistemaOperativo,String sistemaOperativo,List<VersionScriptVista> versiones){public static ImplementacionScriptVista de(ImplementacionScript i){return new ImplementacionScriptVista(i.getId(),i.getVersionSistemaOperativo().getId(),i.getVersionSistemaOperativo().descripcionCompleta(),i.getVersiones().stream().map(VersionScriptVista::de).toList());}}
 public record VersionScriptVista(Integer id,int numero,String contenido,String notasCambio,boolean habilitada,Instant fechaCreacion,List<ParametroScriptVista> parametros){public static VersionScriptVista de(VersionScript v){return new VersionScriptVista(v.getId(),v.getNumeroVersion(),v.getContenido(),v.getNotasCambio(),v.isHabilitada(),v.getFechaCreacion(),v.getParametros().stream().map(p->new ParametroScriptVista(p.getNombre(),p.getTipoDato(),p.isObligatorio(),p.getValorPorDefecto())).toList());}}
 public record ParametroScriptVista(String nombre,TipoDato tipo,boolean obligatorio,String valorPorDefecto){}
}
