package com.komainos.ejecucion.service;
public final class SanitizadorSalida {
 private SanitizadorSalida(){}
 public static String limpiar(String texto,String... secretos){
  String s=texto==null?"":texto;
  for(String secreto:secretos)if(secreto!=null&&!secreto.isEmpty())s=s.replace(secreto,"[PROTEGIDO]");
  s=s.replaceAll("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----","[LLAVE PROTEGIDA]");
  s=s.replaceAll("(?im)(password|contrasena|contraseña|secreto|token)\\s*[:=]\\s*[^\\r\\n]+","$1=[PROTEGIDO]");
  return s.length()>64000?s.substring(0,64000)+"… [salida truncada]":s;
 }
}
