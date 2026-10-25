package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;
import com.komainos.catalogo.model.*;import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.ejecucion.model.*;import com.komainos.ejecucion.service.PuertoAnsible;
import com.komainos.inventario.model.*;import com.komainos.inventario.repository.*;import com.komainos.inventario.service.*;
import com.komainos.seguridad.model.AlcanceUsuario;import com.komainos.shared.exception.*;import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.data.domain.*;import org.hibernate.Hibernate;
import java.time.Clock;import java.util.*;
@Service @RequiredArgsConstructor
public class ServicioEvaluaciones {
 private final ServicioCatalogoAutomatizacion catalogo;private final PuertoAnsible ansible;private final ServicioServidor servidores;
 private final ServicioParametrosSistema parametros;private final PuertoCuentasServicio cuentas;private final UmbralRepositorio umbrales;
 private final MetricaRepositorio metricas;private final EvaluacionRepositorio evaluaciones;private final Clock reloj;
 @Transactional public Evaluacion evaluar(Servidor s,OrdenDetalle detalle,TipoEvaluacion tipo,Integer cuenta){
  if(cuenta==null)cuenta=parametros.obtener().getIdCuentaServicioPredeterminada();
  if(cuenta==null)throw new ReglaNegocioException("Defina una cuenta de servicio antes de analizar el servidor");
  cuentas.exigirAsignable(cuenta,s.familia());var version=catalogo.analisis(s.getVersionSistemaOperativo().getId());
  var e=new Evaluacion();e.setServidor(s);e.setDetalle(detalle);e.setVersion(version);e.setIdCuentaServicio(cuenta);e.definirTipo(tipo);e.setFechaInicio(Tiempo.ahora(reloj));evaluaciones.save(e);
  var solicitud=solicitud(s,version,cuenta,true,tipo==TipoEvaluacion.POSTERIOR,parametros.obtener().getMaxDuracionTareaMinutos());
  ResultadoEjecucion resultado;
  try{resultado=ansible.ejecutar(solicitud);}catch(RuntimeException ex){resultado=ResultadoEjecucion.fallo(false,"No se pudo completar el análisis remoto");}
  if(!resultado.correcto()){e.terminar(resultado.error(),Tiempo.ahora(reloj));return e;}
  try{registrar(e,resultado.metricas(),detalle==null||!detalle.getOrden().esGrupal()?null:detalle.getOrden().getGrupo().getId());e.terminar(null,Tiempo.ahora(reloj));}
  catch(ReglaNegocioException ex){e.getMediciones().clear();e.terminar(ex.getMessage(),Tiempo.ahora(reloj));}
  return e;
 }
 private void registrar(Evaluacion e,List<MedicionInformada> informadas,Integer grupo){
  Map<String,Metrica> porCodigo=new HashMap<>();metricas.findAllByOrderByNombreAsc().stream().filter(Metrica::isActivo).forEach(m->porCodigo.put(m.getCodigo(),m));
  var vigentes=umbrales.findAplicables();var contexto=grupo==null?ContextoEvaluacion.individual(e.getServidor()):ContextoEvaluacion.enGrupo(e.getServidor(),grupo);
  Set<String> recibidas=new HashSet<>();
  for(var dato:informadas){Metrica metrica=porCodigo.get(dato.codigo());if(metrica==null)continue;recibidas.add(dato.codigo());
   if(metrica.isEsLista()!=(dato.elementos()!=null))throw new ReglaNegocioException("Tipo de salida incorrecto para "+dato.codigo());
   var m=new MedicionMetrica();m.setEvaluacion(e);m.setMetrica(metrica);m.setInstancia(dato.instancia()==null?"":dato.instancia());
   if(metrica.isEsLista())dato.elementos().forEach(x->m.getElementos().add(metrica.getTipoDato().canonico(x,false)));
   else {if(dato.valor()==null)throw new ReglaNegocioException("Falta el valor de "+dato.codigo());metrica.getTipoDato().validarLimite(dato.valor(),"informado");m.setValor(dato.valor());}
   for(var u:vigentes)if(u.getMetrica().getId().equals(metrica.getId())){var a=UmbralAplicado.resolver(u,contexto);NivelAlerta nivel=u.esDeLista()?a.evaluarLista(m.getElementos()).nivel():a.evaluar(m.getValor());var eu=new EvaluacionUmbral();eu.setMedicion(m);eu.aplicar(a,nivel,Tiempo.ahora(reloj));m.getUmbrales().add(eu);}
   e.getMediciones().add(m);
  }
  for(var u:vigentes)if(!recibidas.contains(u.getMetrica().getCodigo()))throw new ReglaNegocioException("El análisis no informó la métrica requerida: "+u.getMetrica().getCodigo());
  if(e.getMediciones().isEmpty())throw new ReglaNegocioException("El análisis no informó métricas activas registradas");
 }
 public static SolicitudEjecucion solicitud(Servidor s,VersionScript v,Integer cuenta,boolean analisis,boolean posterior,int max){
  Map<String,Object> valores=new LinkedHashMap<>();for(var p:v.getParametros()){String valor=p.getValorPorDefecto();if(valor==null){if(p.isObligatorio())throw new ReglaNegocioException("Falta el parámetro "+p.getNombre());continue;}valores.put(p.getNombre(),switch(p.getTipoDato()){case INTEGER->Integer.valueOf(valor);case DECIMAL->new java.math.BigDecimal(valor);case BOOLEAN->Boolean.valueOf(valor);case STRING->valor;});}
  return new SolicitudEjecucion(s.getId(),s.getHostname(),s.getDireccionIp(),s.familia(),cuenta,v.getContenido(),valores,analisis,posterior,max);
 }
 public static NivelAlerta nivel(Evaluacion e){return e.getMediciones().stream().flatMap(m->m.getUmbrales().stream()).map(EvaluacionUmbral::getNivel).reduce(NivelAlerta.NORMAL,NivelAlerta::peor);}
 public static Set<Integer> activados(Evaluacion e){Set<Integer> ids=new HashSet<>();e.getMediciones().forEach(m->m.getUmbrales().stream().filter(u->u.getNivel()!=NivelAlerta.NORMAL).forEach(u->ids.add(u.getIdUmbral())));return ids;}
 @Transactional(readOnly=true)public Page<Evaluacion> historial(Integer id,AlcanceUsuario alcance,Pageable p){servidores.obtener(id,alcance);var pagina=evaluaciones.findByServidorIdOrderByFechaInicioDesc(id,p);pagina.forEach(ServicioEvaluaciones::cargar);return pagina;}
 public static void cargar(Evaluacion e){Hibernate.initialize(e.getVersion().getImplementacion().getScript());e.getMediciones().forEach(m->{Hibernate.initialize(m.getMetrica());Hibernate.initialize(m.getElementos());Hibernate.initialize(m.getUmbrales());});}
}
