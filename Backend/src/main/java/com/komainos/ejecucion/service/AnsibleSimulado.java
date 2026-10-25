package com.komainos.ejecucion.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.List;
/** Modo local por defecto: sin procesos, archivos de credenciales ni conexiones remotas. */
@Service @RequiredArgsConstructor @ConditionalOnProperty(prefix="komainos.automatizacion",name="modo",havingValue="SIMULADO",matchIfMissing=true)
public class AnsibleSimulado implements PuertoAnsible {
 private final ObjectMapper json;private final PropiedadesAutomatizacion propiedades;
 public ResultadoEjecucion probarConexion(SolicitudEjecucion s){if("SIN_CONEXION".equals(propiedades.escenario(s.hostname())))return ResultadoEjecucion.fallo(true,"Conexión no disponible (simulada)");return new ResultadoEjecucion(true,0,"Conectividad, autenticación y permisos simulados correctamente",null,List.of());}
 public ResultadoEjecucion ejecutar(SolicitudEjecucion s){
  String escenario=propiedades.escenario(s.hostname());
  if("SIN_CONEXION".equals(escenario))return ResultadoEjecucion.fallo(true,"Conexión no disponible (simulada)");
  if(!s.analisis())return "FALLO_TAREA".equals(escenario)?ResultadoEjecucion.fallo(true,"Error de mantenimiento simulado"):new ResultadoEjecucion(true,0,"Tarea completada correctamente (simulación local)",null,List.of());
  boolean normal=s.posterior()&&!"NO_CONFORME".equals(escenario);
  String salida="""
   {"metricas":[{"codigo":"cpu_uso_pct","valor":%d},{"codigo":"memoria_uso_pct","valor":%d},
   {"codigo":"disco_uso_pct","instancia":"sistema","valor":%d},{"codigo":"carga_promedio","valor":1},
   {"codigo":"uso_swap","valor":5},{"codigo":"puertos_escucha","elementos":[22,80,443,5432]},
   {"codigo":"servicios_ejecucion","elementos":["crond","rsyslog","sshd"]},
   {"codigo":"usuarios_locales","elementos":["admin","deploy","root"]}]}
   """.formatted("CRITICO".equals(escenario)?96:normal?35:58,normal?52:82,normal?55:86);
  return new ResultadoEjecucion(true,0,salida,null,ContratoAnalisis.leer(salida,json));
 }
}
