package com.komainos.ejecucion.model;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import java.util.Map;
public record SolicitudEjecucion(Integer idServidor,String hostname,String direccionIp,FamiliaSistemaOperativo familia,
 Integer idCuentaServicio,String contenido,Map<String,Object> parametros,boolean analisis,boolean posterior,int maxMinutos) {}
