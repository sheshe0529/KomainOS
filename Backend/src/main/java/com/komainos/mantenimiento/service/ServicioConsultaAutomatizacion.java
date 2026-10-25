package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;import com.komainos.ejecucion.config.PropiedadesAutomatizacion;import com.komainos.seguridad.model.AlcanceUsuario;import com.komainos.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.data.domain.*;import org.springframework.data.jpa.domain.Specification;import org.hibernate.Hibernate;import java.util.*;
@Service @RequiredArgsConstructor
public class ServicioConsultaAutomatizacion {
 private final com.komainos.inventario.service.ServicioParametrosSistema parametros;private final ServicioConsultaOrdenes consulta;private final OrdenRepositorio ordenes;private final OrdenDetalleRepositorio detalles;private final MopRepositorio mops;private final MopTareaRepositorio tareas;private final EvaluacionRepositorio evaluaciones;private final SolicitudAutorizacionRepositorio solicitudes;private final ValidacionFuncionalRepositorio validaciones;private final ValidacionTecnicaRepositorio tecnicas;private final IncidenciaRepositorio incidencias;private final PropiedadesAutomatizacion propiedades;
 @Transactional(readOnly=true)public FichaAutomatizacion ficha(Integer id,AlcanceUsuario alcance){var o=consulta.obtener(id,alcance);var visibles=o.getDetalles().stream().filter(d->alcance.veTodoElInventario()||d.getServidor().esVisiblePara(alcance.usuarioId())).map(OrdenDetalle::getId).collect(java.util.stream.Collectors.toSet());
  var planes=mops.findByDetalleOrdenId(id).stream().filter(m->visibles.contains(m.getDetalle().getId())).toList();planes.forEach(m->m.getTareas().forEach(t->{Hibernate.initialize(t.getTarea());if(t.getVersion()!=null)Hibernate.initialize(t.getVersion().getImplementacion().getScript());}));
  List<Evaluacion> evals=new ArrayList<>();for(Integer d:visibles)evals.addAll(evaluaciones.findByDetalleIdOrderByFechaInicioAsc(d));evals.forEach(ServicioEvaluaciones::cargar);
  var sol=solicitudes.findByOrdenIdOrderByNumeroRondaAsc(id);sol.forEach(s->{Hibernate.initialize(s.getDecisiones());Hibernate.initialize(s.getRegla().getEntorno());s.getDecisiones().forEach(a->Hibernate.initialize(a.getUsuarioDecision()));});
  var vf=validaciones.findByOrdenId(id).stream().filter(v->alcance.veTodoElInventario()||v.getResponsable().getId().equals(alcance.usuarioId())).toList();vf.forEach(v->Hibernate.initialize(v.getResponsable()));
  return new FichaAutomatizacion(o,planes,evals,sol,vf,tecnicas.findByDetalleOrdenId(id).stream().filter(v->visibles.contains(v.getDetalle().getId())).toList(),incidencias.findByDetalleOrdenId(id).stream().filter(i->visibles.contains(i.getDetalle().getId())).toList(),propiedades.simulada(),parametros.obtener().getMaxDuracionMopMinutos());
 }
 @Transactional(readOnly=true)public Page<Orden> sala(EtapaOrden etapa,Integer entorno,Integer criticidad,Pageable p){Specification<Orden> filtro=(r,q,cb)->{List<jakarta.persistence.criteria.Predicate> ps=new ArrayList<>();if(etapa!=null)ps.add(r.get("estado").in(Arrays.stream(EstadoOrden.values()).filter(e->e.etapa()==etapa).toList()));if(criticidad!=null)ps.add(cb.equal(r.get("nivelCriticidad").get("id"),criticidad));if(entorno!=null){var sub=q.subquery(Integer.class);var d=sub.from(OrdenDetalle.class);sub.select(d.get("id")).where(cb.equal(d.get("orden"),r),cb.equal(d.get("servidor").get("entorno").get("id"),entorno));ps.add(cb.exists(sub));}return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));};var pagina=ordenes.findAll(filtro,p);pagina.forEach(o->Hibernate.initialize(o.getDetalles()));return pagina;}
 @Transactional(readOnly=true)public Page<OrdenDetalle> cola(Pageable p){return detalles.cola(Set.of(EstadoDetalleOrden.EN_COLA,EstadoDetalleOrden.EN_EJECUCION),Set.of(OrigenOrden.LANZAMIENTO_INMEDIATO,OrigenOrden.CONDICION_CRITICA),p);}
 @Transactional(readOnly=true)public Page<MopTarea> usosVersion(Integer id,Pageable p){return tareas.findByVersionIdOrderByIdDesc(id,p);}
 @Transactional(readOnly=true)public Map<String,Long> indicadores(){Map<String,Long> m=new LinkedHashMap<>();for(var etapa:EtapaOrden.values())m.put(etapa.name(),ordenes.count((r,q,cb)->r.get("estado").in(Arrays.stream(EstadoOrden.values()).filter(e->e.etapa()==etapa).toList())));return m;}
 /** Agrega solo los filtros presentes: evita parámetros nulos sin tipo en los enumerados PostgreSQL. */
 @Transactional(readOnly=true)public Page<Incidencia> incidencias(EstadoIncidencia estado,Integer servidor,String texto,Pageable p){
  Specification<Incidencia> filtro=(r,q,cb)->{
   List<jakarta.persistence.criteria.Predicate> condiciones=new ArrayList<>();
   if(estado!=null)condiciones.add(cb.equal(r.get("estado"),estado));
   if(servidor!=null)condiciones.add(cb.equal(r.get("detalle").get("servidor").get("id"),servidor));
   if(texto!=null&&!texto.isBlank()){
    String patron="%"+texto.trim().toLowerCase(Locale.ROOT)+"%";
    condiciones.add(cb.or(cb.like(cb.lower(r.get("detalle").get("servidor").get("hostname")),patron),cb.like(cb.lower(r.get("detalle").get("orden").get("codigo")),patron)));
   }
   return cb.and(condiciones.toArray(jakarta.persistence.criteria.Predicate[]::new));
  };
  return incidencias.findAll(filtro,PageRequest.of(p.getPageNumber(),p.getPageSize(),Sort.by(Sort.Order.desc("fechaApertura"),Sort.Order.desc("id"))));
 }
 @Transactional(readOnly=true)public Incidencia incidencia(Integer id){return incidencias.findById(id).orElseThrow(()->RecursoNoEncontradoException.de("la incidencia",id));}
 public record FichaAutomatizacion(Orden orden,List<Mop> mops,List<Evaluacion> evaluaciones,List<SolicitudAutorizacion> autorizaciones,List<ValidacionFuncional> validaciones,List<ValidacionTecnica> validacionesTecnicas,List<Incidencia> incidencias,boolean simulado,int maxDuracionMop){}
}
