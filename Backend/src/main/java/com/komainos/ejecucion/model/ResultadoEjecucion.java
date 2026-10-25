package com.komainos.ejecucion.model;
import java.util.List;
public record ResultadoEjecucion(boolean simulado,int codigoRetorno,String salida,String error,List<MedicionInformada> metricas) {
 public boolean correcto(){return codigoRetorno==0;}
 public static ResultadoEjecucion fallo(boolean simulado,String error){return new ResultadoEjecucion(simulado,1,"",error,List.of());}
}
