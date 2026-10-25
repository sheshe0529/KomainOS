package com.komainos.ejecucion.service;
import com.fasterxml.jackson.databind.*;
import com.komainos.ejecucion.model.MedicionInformada;
import com.komainos.shared.exception.ReglaNegocioException;
import java.util.*;
/** Salida JSON estricta: metricas:[{codigo,instancia?,valor|elementos}]. Una clave/instancia aparece una sola vez. */
public final class ContratoAnalisis {
 private ContratoAnalisis(){}
 public static List<MedicionInformada> leer(String salida,ObjectMapper json) {
  try {var raiz=json.readTree(salida);if(raiz==null||!raiz.path("metricas").isArray()||raiz.path("metricas").isEmpty()||raiz.path("metricas").size()>1000)throw new IllegalArgumentException();
   List<MedicionInformada> resultado=new ArrayList<>();Set<String> vistas=new HashSet<>();
   for(var m:raiz.path("metricas")){String codigo=m.path("codigo").asText("");String instancia=m.path("instancia").asText("");
    if(!codigo.matches("[A-Za-z0-9_.-]{1,100}")||instancia.length()>255||!vistas.add(codigo+"/"+instancia)||m.has("valor")==m.has("elementos"))throw new IllegalArgumentException();
    if(m.has("valor")&&!m.path("valor").isNumber())throw new IllegalArgumentException();
    List<String> elementos=null;if(m.has("elementos")){if(!m.path("elementos").isArray()||m.path("elementos").size()>1000)throw new IllegalArgumentException();elementos=new ArrayList<>();for(var x:m.path("elementos")){if(!x.isValueNode()||x.isNull()||x.asText().length()>255)throw new IllegalArgumentException();elementos.add(x.asText());}}
    resultado.add(new MedicionInformada(codigo,instancia,m.has("valor")?m.path("valor").decimalValue():null,elementos));
   }return List.copyOf(resultado);
  }catch(Exception e){throw new ReglaNegocioException("El análisis no cumple el contrato JSON de métricas");}
 }
}
