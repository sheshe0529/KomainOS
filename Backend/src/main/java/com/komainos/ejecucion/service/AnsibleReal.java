package com.komainos.ejecucion.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.*;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.nio.file.*;import java.nio.file.attribute.PosixFilePermissions;import java.time.Duration;import java.util.*;import java.util.concurrent.TimeUnit;
/** Adaptador preparado para un controlador Linux. Nunca se instancia ni ejecuta en modo SIMULADO. */
@Service @ConditionalOnProperty(prefix="komainos.automatizacion",name="modo",havingValue="REAL")
public class AnsibleReal implements PuertoAnsible {
 private final PuertoConexionAutomatizacion conexiones;private final ObjectMapper json;private final PropiedadesAutomatizacion propiedades;
 public AnsibleReal(PuertoConexionAutomatizacion c,ObjectMapper j,PropiedadesAutomatizacion p){
  if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))throw new IllegalStateException("El adaptador REAL requiere un controlador Linux; utilice SIMULADO en Windows");conexiones=c;json=j;propiedades=p;
 }
 public ResultadoEjecucion probarConexion(SolicitudEjecucion s){String codigo=s.familia()==FamiliaSistemaOperativo.WINDOWS?"if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { exit 1 }; Write-Output 'Conectividad y permisos verificados'":"#!/bin/bash\nset -eu\nid -u | grep -qx 0 || sudo -n true\n";
  return ejecutar(new SolicitudEjecucion(s.idServidor(),s.hostname(),s.direccionIp(),s.familia(),s.idCuentaServicio(),codigo,Map.of(),false,false,1));}
 public ResultadoEjecucion ejecutar(SolicitudEjecucion s){
  Path directorio=null;PuertoConexionAutomatizacion.Conexion conexion=null;
  try{
   conexion=conexiones.resolver(s.idCuentaServicio(),s.familia());
   directorio=Files.createTempDirectory("komainos-ansible-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
   boolean windows=s.familia()==FamiliaSistemaOperativo.WINDOWS;Map<String,Object> host=new LinkedHashMap<>();host.put("ansible_host",s.direccionIp());host.put("ansible_user",conexion.usuario());
   if(windows){host.put("ansible_connection","winrm");host.put("ansible_port",5986);host.put("ansible_winrm_scheme","https");host.put("ansible_winrm_transport","ntlm");host.put("ansible_winrm_server_cert_validation","validate");}
   if(conexion.llave()){Path llave=escribir(directorio,"llave_privada",conexion.secreto());host.put("ansible_ssh_private_key_file",llave.toString());}else host.put("ansible_password",conexion.secreto());
   if(conexion.su()!=null){host.put("ansible_become",true);host.put("ansible_become_method","su");host.put("ansible_become_user","root");host.put("ansible_become_password",conexion.su());}
   Path inventario=escribir(directorio,"inventario.json",json.writeValueAsString(Map.of("all",Map.of("hosts",Map.of("objetivo",host)))));
   Path codigo=escribir(directorio,windows?"tarea.ps1":"tarea.sh",s.contenido());StringBuilder comando=new StringBuilder(citar(codigo.toString(),windows));
   for(var parametro:s.parametros().entrySet()){if(!parametro.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))return ResultadoEjecucion.fallo(false,"Nombre de parámetro inválido");if(windows)comando.append(" -").append(parametro.getKey());comando.append(' ').append(citar(String.valueOf(parametro.getValue()),windows));}
   Map<String,Object> modulo=new LinkedHashMap<>();modulo.put("cmd",comando.toString());if(!windows)modulo.put("executable","/bin/bash");
   var tarea=Map.of("name","komainos_resultado","ansible.builtin.script",modulo);
   Path playbook=escribir(directorio,"playbook.json",json.writeValueAsString(List.of(Map.of("name","KomainOS","hosts","objetivo","gather_facts",false,"tasks",List.of(tarea)))));
   try(var recurso=getClass().getResourceAsStream("/ansible/callback_plugins/komainos_json.py")){if(recurso==null)return ResultadoEjecucion.fallo(false,"Falta el callback de resultados de Ansible");escribir(directorio,"komainos_json.py",new String(recurso.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   Path salida=directorio.resolve("salida.log");var pb=new ProcessBuilder(propiedades.ejecutable(),"-i",inventario.toString(),playbook.toString()).redirectErrorStream(true).redirectOutput(salida.toFile());
   pb.environment().put("ANSIBLE_STDOUT_CALLBACK","komainos_json");pb.environment().put("ANSIBLE_CALLBACK_PLUGINS",directorio.toString());pb.environment().put("ANSIBLE_NOCOLOR","1");pb.environment().put("ANSIBLE_HOST_KEY_CHECKING","True");
   Process proceso=pb.start();if(!proceso.waitFor(Math.max(1,s.maxMinutos()),TimeUnit.MINUTES)){proceso.descendants().forEach(ProcessHandle::destroy);proceso.destroy();if(!proceso.waitFor(5,TimeUnit.SECONDS))proceso.destroyForcibly();return ResultadoEjecucion.fallo(false,"La tarea excedió su tiempo máximo de ejecución");}
   if(!Files.exists(salida)||Files.size(salida)>2_000_000)return ResultadoEjecucion.fallo(false,"El resultado de Ansible excede el tamaño permitido");
   String bruto=Files.readString(salida);String linea=bruto.lines().filter(l->l.startsWith("KOMAINOS_JSON=")).reduce((a,z)->z).orElse(null);if(linea==null)return ResultadoEjecucion.fallo(false,"Ansible no devolvió un resultado estructurado");
   var r=json.readTree(linea.substring("KOMAINOS_JSON=".length()));int retorno=r.path("rc").asInt(proceso.exitValue());String stdout=SanitizadorSalida.limpiar(r.path("stdout").asText(""),conexion.secreto(),conexion.su());String error=retorno==0?null:SanitizadorSalida.limpiar(r.path("error").asText("Error de ejecución remota"),conexion.secreto(),conexion.su());
   return new ResultadoEjecucion(false,retorno,stdout,error,s.analisis()&&retorno==0?ContratoAnalisis.leer(stdout,json):List.of());
  }catch(InterruptedException e){Thread.currentThread().interrupt();return ResultadoEjecucion.fallo(false,"Ejecución interrumpida");}
  catch(Exception e){return ResultadoEjecucion.fallo(false,"No se pudo completar la conexión o ejecución mediante Ansible");}
  finally{if(directorio!=null)try(var paths=Files.walk(directorio)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}catch(Exception ignored){/* Sin material sensible en logs. */}}
 }
 private static Path escribir(Path carpeta,String nombre,String texto)throws java.io.IOException{Path p=carpeta.resolve(nombre);Files.createFile(p,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));return Files.writeString(p,texto);}
 public static String citar(String s,boolean windows){return windows?"'"+s.replace("'","''")+"'":"'"+s.replace("'","'\"'\"'")+"'";}
}
