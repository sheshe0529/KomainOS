package com.komainos.catalogo.service;
import com.komainos.catalogo.model.*;
import com.komainos.catalogo.repository.*;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.shared.exception.*;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.hibernate.Hibernate;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor
public class ServicioCatalogoAutomatizacion {
 private final ScriptRepositorio scripts; private final ImplementacionScriptRepositorio implementaciones;
 private final VersionScriptRepositorio versiones; private final TareaRepositorio tareas;
 private final ServicioCatalogos catalogos; private final ServicioParametrosSistema parametros;
 private final UmbralRepositorio umbrales; private final ServicioAuditoria auditoria; private final Clock reloj;
 @Transactional(readOnly=true) public Page<Script> scripts(Pageable p) { var pagina=scripts.findAll(p);pagina.forEach(this::cargar);return pagina; }
 @Transactional(readOnly=true) public Script script(Integer id) {var s=scripts.findById(id).orElseThrow(()->RecursoNoEncontradoException.de("el script",id));cargar(s);return s;}
 private void cargar(Script s) {s.getImplementaciones().forEach(i->{Hibernate.initialize(i.getVersionSistemaOperativo().getSistemaOperativo());i.getVersiones().forEach(v->Hibernate.initialize(v.getParametros()));});}
 @Transactional public Script guardarScript(Integer id,String nombre,TipoScript tipo,String descripcion,boolean activo,Actor actor) {
  scripts.bloquearCatalogo();
  if(id==null?scripts.existsByNombreIgnoreCase(nombre.trim()):scripts.existsByNombreIgnoreCaseAndIdNot(nombre.trim(),id)) throw new ConflictoException("Ya existe un script con ese nombre");
  Script s=id==null?Script.nuevo(nombre,tipo,descripcion):script(id);
  if(s.getTipo()!=tipo) throw new ReglaNegocioException("El tipo de un script no cambia después de registrarlo");
  if(activo && tipo==TipoScript.ANALISIS) for(var i:s.getImplementaciones()) exigirAnalisisUnico(i.getVersionSistemaOperativo().getId(),s.getId());
  s.actualizar(nombre,descripcion,activo);scripts.save(s);auditar("GUARDAR_SCRIPT",s.getId(),actor);return s;
 }
 /** RF24: un único script de análisis activo por versión del SO; bloqueo entre publicaciones concurrentes. */
 private void exigirAnalisisUnico(Integer idVersion,Integer propio) {
  for(var i:implementaciones.findByVersionSistemaOperativoId(idVersion)) if(i.getScript().isActivo() && i.getScript().getTipo()==TipoScript.ANALISIS && !Objects.equals(i.getScript().getId(),propio) && i.ultimaHabilitada().isPresent()) throw new ConflictoException("Ya existe un script de análisis habilitado para ese sistema operativo y versión");
 }
 @Transactional public ImplementacionScript implementar(Integer idScript,Integer idSo,Actor actor) {
  scripts.bloquearCatalogo();
  var so=catalogos.obtenerVersion(idSo); if(!so.isActivo()||!so.getSistemaOperativo().isActivo()) throw new ReglaNegocioException("La versión de sistema operativo está inactiva");
  var s=script(idScript); if(s.getImplementaciones().stream().anyMatch(i->i.getVersionSistemaOperativo().getId().equals(idSo))) throw new ConflictoException("El script ya tiene una implementación para esa versión");
  var i=ImplementacionScript.nueva(s,so,Tiempo.ahora(reloj));implementaciones.save(i);s.getImplementaciones().add(i);auditar("CREAR_IMPLEMENTACION",i.getId(),actor);return i;
 }
 @Transactional public VersionScript publicar(Integer idImpl,String contenido,String notas,List<DatosParametro> datos,Actor actor) {
  scripts.bloquearCatalogo();
  var i=implementaciones.findConDetalleById(idImpl).orElseThrow(()->RecursoNoEncontradoException.de("la implementación",idImpl));
  // Serializa el número de versión y la unicidad del análisis por SO, incluso entre scripts distintos.
  catalogos.obtenerVersion(i.getVersionSistemaOperativo().getId());
  if(i.getScript().getTipo()==TipoScript.ANALISIS) exigirAnalisisUnico(i.getVersionSistemaOperativo().getId(),i.getScript().getId());
  Integer ultimo=versiones.ultimoNumero(idImpl); var v=VersionScript.nueva(i,ultimo==null?1:ultimo+1,contenido,notas,Tiempo.ahora(reloj));
  Set<String> nombres=new HashSet<>(); int orden=0;
  for(var d:datos) { if(!nombres.add(d.nombre())) throw new ReglaNegocioException("Hay parámetros repetidos"); validarParametro(d); v.getParametros().add(ParametroScript.nuevo(v,d.nombre(),d.tipo(),d.obligatorio(),d.defecto(),++orden)); }
  versiones.save(v);i.getVersiones().add(v);auditar("PUBLICAR_VERSION_SCRIPT",v.getId(),actor);return v;
 }
 public static void validarParametro(DatosParametro p) {
  if(p.defecto()==null||p.defecto().isBlank()) {if(p.obligatorio())throw new ReglaNegocioException("El parámetro obligatorio "+p.nombre()+" requiere valor para ejecutar automáticamente");return;}
  try {switch(p.tipo()) {case INTEGER -> Integer.parseInt(p.defecto());case DECIMAL -> new java.math.BigDecimal(p.defecto());case BOOLEAN -> {if(!Set.of("true","false").contains(p.defecto())) throw new IllegalArgumentException();}case STRING -> {}}} catch(IllegalArgumentException e) {throw new ReglaNegocioException("Valor por defecto inválido para "+p.nombre());}
 }
 @Transactional public VersionScript habilitar(Integer id,boolean habilitada,Actor actor) { scripts.bloquearCatalogo();var v=version(id);if(habilitada&&v.getImplementacion().getScript().getTipo()==TipoScript.ANALISIS) exigirAnalisisUnico(v.getImplementacion().getVersionSistemaOperativo().getId(),v.getImplementacion().getScript().getId());v.habilitar(habilitada);auditar("ESTADO_VERSION_SCRIPT",id,actor);return v; }
 @Transactional(readOnly=true) public VersionScript version(Integer id) {return versiones.findConDetalleById(id).orElseThrow(()->RecursoNoEncontradoException.de("la versión de script",id));}
 @Transactional(readOnly=true) public Optional<VersionScript> compatible(Integer idScript,Integer idSo) {return script(idScript).isActivo()?script(idScript).getImplementaciones().stream().filter(i->i.getVersionSistemaOperativo().getId().equals(idSo)).flatMap(i->i.ultimaHabilitada().stream()).findFirst():Optional.empty();}
 @Transactional(readOnly=true) public Optional<VersionScript> buscarAnalisis(Integer idSo) {return implementaciones.findByVersionSistemaOperativoId(idSo).stream().filter(i->i.getScript().isActivo()&&i.getScript().getTipo()==TipoScript.ANALISIS).flatMap(i->i.ultimaHabilitada().stream()).findFirst().map(v->version(v.getId()));}
 @Transactional(readOnly=true) public VersionScript analisis(Integer idSo) {return buscarAnalisis(idSo).orElseThrow(()->new ReglaNegocioException("No hay script de análisis habilitado compatible con el servidor"));}
 @Transactional(readOnly=true) public Page<Tarea> tareas(Pageable p) {return tareas.findAll(p);}
 @Transactional(readOnly=true) public List<Tarea> activadas(Set<Integer> ids) {return ids.isEmpty()?List.of():tareas.findActivadas(ids);}
 @Transactional public Tarea guardarTarea(Integer id,DatosTarea d,Actor actor) {
  if(d.duracion()>parametros.obtener().getMaxDuracionTareaMinutos())throw new ReglaNegocioException("La duración estimada supera el máximo por tarea");
  var script=script(d.idScript());if(script.getTipo()!=TipoScript.MANTENIMIENTO)throw new ReglaNegocioException("Una tarea requiere un script de mantenimiento");
  if(d.activo()&&(!script.isActivo()||script.getImplementaciones().stream().noneMatch(i->i.ultimaHabilitada().isPresent())))throw new ReglaNegocioException("La tarea requiere un script con implementación habilitada");
  if(umbrales.countByIdIn(d.umbrales())!=d.umbrales().size())throw new ReglaNegocioException("Uno de los umbrales no existe");
  var t=id==null?Tarea.nueva():tareas.findById(id).orElseThrow(()->RecursoNoEncontradoException.de("la tarea",id));t.actualizar(script,d.nombre(),d.descripcion(),d.afectacion(),d.prioridad(),d.duracion(),d.activo(),d.umbrales());tareas.save(t);auditar("GUARDAR_TAREA",t.getId(),actor);return t;
 }
 private void auditar(String op,Integer id,Actor actor){auditoria.registrar(actor,Operacion.de(op,"catalogo_automatizacion",id));}
 public record DatosParametro(String nombre,TipoDato tipo,boolean obligatorio,String defecto){}
 public record DatosTarea(Integer idScript,String nombre,String descripcion,NivelAfectacion afectacion,int prioridad,int duracion,boolean activo,Set<Integer> umbrales){}
}
