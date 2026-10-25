package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;
import com.komainos.ejecucion.model.*;import com.komainos.ejecucion.service.PuertoAnsible;
import com.komainos.inventario.model.*;import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.shared.util.Tiempo;import com.komainos.shared.exception.ReglaNegocioException;
import org.springframework.stereotype.Service;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PreDestroy;import java.time.*;import java.util.*;import java.util.concurrent.*;
/** RF51/RF58-RF60: las reservas se confirman en BD antes de abrir el transporte. Ansible corre fuera de la transacción. */
@Service
public class ServicioOrquestador {
 private final OrdenRepositorio ordenes;private final OrdenDetalleRepositorio detalles;private final MopRepositorio mops;private final MopTareaRepositorio tareas;private final ConfirmacionFueraVentanaRepositorio excepciones;
 private final ServicioParametrosSistema parametros;private final ServicioCicloMantenimiento ciclo;private final PuertoAnsible ansible;private final Clock reloj;private final TransactionTemplate tx;
 private final ExecutorService trabajadores=Executors.newVirtualThreadPerTaskExecutor();
 public ServicioOrquestador(OrdenRepositorio ordenes,OrdenDetalleRepositorio detalles,MopRepositorio mops,MopTareaRepositorio tareas,ConfirmacionFueraVentanaRepositorio excepciones,ServicioParametrosSistema parametros,ServicioCicloMantenimiento ciclo,PuertoAnsible ansible,Clock reloj,PlatformTransactionManager tm){this.ordenes=ordenes;this.detalles=detalles;this.mops=mops;this.tareas=tareas;this.excepciones=excepciones;this.parametros=parametros;this.ciclo=ciclo;this.ansible=ansible;this.reloj=reloj;tx=new TransactionTemplate(tm);}
 public int despachar(){int cantidad=0;Integer id;while((id=tx.execute(estado->reservar()))!=null){final Integer detalle=id;trabajadores.submit(()->ejecutar(detalle));if(++cantidad>=500)break;}return cantidad;}
 /** Entrada determinista para pruebas locales; conserva los mismos bloqueos y reglas del despacho asíncrono. */
 public int drenar(){int cantidad=0;for(int vuelta=0;vuelta<500;vuelta++){List<Future<?>> lote=new ArrayList<>();Integer id;while((id=tx.execute(estado->reservar()))!=null){final Integer detalle=id;lote.add(trabajadores.submit(()->ejecutar(detalle)));cantidad++;if(lote.size()>=100)break;}if(lote.isEmpty())break;for(var f:lote)try{f.get(5,TimeUnit.MINUTES);}catch(Exception e){throw new IllegalStateException("No se pudo completar la ejecución de prueba",e);}}return cantidad;}
 private Integer reservar(){
  ordenes.bloquearTransaccion(1263489103L);Instant ahora=Tiempo.ahora(reloj);var p=parametros.obtener();var activos=detalles.findByEstado(EstadoDetalleOrden.EN_EJECUCION);Set<Integer> ocupados=new HashSet<>();activos.forEach(d->ocupados.add(d.getServidor().getId()));long normales=activos.stream().filter(d->d.getOrden().getOrigen()!=OrigenOrden.LANZAMIENTO_INMEDIATO).count();
  var candidatas=ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(Set.of(EstadoOrden.AUTORIZADA,EstadoOrden.EN_COLA,EstadoOrden.EN_EJECUCION));
  candidatas.sort(Comparator.comparingInt((Orden o)->o.prioridad()==PrioridadOrden.ALTA?0:1).thenComparingInt(o->o.getNivelCriticidad().getPrioridad()).thenComparing(Orden::getFechaInicioProgramada).thenComparing(Orden::getId));
  for(var o:candidatas){
   if(ahora.isBefore(o.getFechaInicioProgramada()))continue;
   if(o.getEstado()==EstadoOrden.AUTORIZADA){o.cambiarEstado(EstadoOrden.EN_COLA,null,"Fecha autorizada alcanzada",ahora);for(var d:o.getDetalles())if(d.getEstado()==EstadoDetalleOrden.PENDIENTE)d.cambiarEstado(EstadoDetalleOrden.EN_COLA,ahora);}
   boolean inmediata=o.getOrigen()==OrigenOrden.LANZAMIENTO_INMEDIATO;
   if(!inmediata&&normales>=p.getMaxEjecucionesConcurrentes())continue;
   if(o.esGrupal()&&o.getModoEjecucionAplicado()==ModoEjecucion.SECUENCIAL&&o.getDetalles().stream().anyMatch(d->d.getEstado()==EstadoDetalleOrden.EN_EJECUCION))continue;
   var piloto=o.getDetalles().stream().filter(OrdenDetalle::isEsServidorPiloto).findFirst();
   for(var d:o.getDetalles()){
    if(d.getEstado()!=EstadoDetalleOrden.EN_COLA||ocupados.contains(d.getServidor().getId()))continue;
    if(piloto.isPresent()&&!piloto.get().getId().equals(d.getId())&&piloto.get().getEstado()!=EstadoDetalleOrden.FINALIZADO)continue;
    if(d.getServidor().getEstado()!=EstadoServidor.ACTIVO){d.omitirSiPendiente();continue;}
    if(!excepciones.existsByOrdenId(o.getId())&&!ahora.isBefore(o.getFinVentanaAplicada())){if(o.getEstado()==EstadoOrden.EN_COLA)o.cambiarEstado(EstadoOrden.EN_EJECUCION,null,"Comprobación de inicio",ahora);ciclo.registrarIncidencia(d,OrigenIncidencia.TIEMPO_EXCEDIDO,"No se inició la intervención: terminó la ventana permisiva");break;}
    if(o.getEstado()==EstadoOrden.EN_COLA)o.cambiarEstado(EstadoOrden.EN_EJECUCION,null,"Capacidad y exclusión verificadas",ahora);
    d.cambiarEstado(EstadoDetalleOrden.EN_EJECUCION,ahora);return d.getId();
   }
  }return null;
 }
 private Trabajo siguiente(Integer id){
  ordenes.bloquearTransaccion(1263489103L);var d=detalles.findById(id).orElseThrow();var o=d.getOrden();var mop=mops.findByDetalleId(id).orElseThrow();Instant ahora=Tiempo.ahora(reloj);
  if(o.getEstado()!=EstadoOrden.EN_EJECUCION){mop.getTareas().forEach(MopTarea::omitir);if(d.getEstado()==EstadoDetalleOrden.EN_EJECUCION)d.cambiarEstado(EstadoDetalleOrden.FALLIDO,ahora);return null;}
  var siguiente=mop.getTareas().stream().filter(t->t.getEstado()==EstadoTareaMop.PENDIENTE).findFirst();
  boolean fuera=!excepciones.existsByOrdenId(o.getId())&&!ahora.isBefore(o.getFinVentanaAplicada());boolean excedido=Duration.between(d.getFechaRealInicio(),ahora).compareTo(Duration.ofMinutes(parametros.obtener().getMaxDuracionMopMinutos()))>=0;
  if(fuera||excedido){mop.getTareas().forEach(MopTarea::omitir);d.cambiarEstado(EstadoDetalleOrden.FALLIDO,ahora);ciclo.registrarIncidencia(d,OrigenIncidencia.TIEMPO_EXCEDIDO,fuera?"Se alcanzó el fin de la ventana; no se iniciaron tareas pendientes":"Se superó la duración máxima por MOP");return null;}
  if(siguiente.isEmpty()){d.cambiarEstado(EstadoDetalleOrden.FINALIZADO,ahora);return null;}
  var t=siguiente.get();if(t.getVersion()==null)throw new ReglaNegocioException("No existe versión fijada para la tarea");
  var solicitud=ServicioEvaluaciones.solicitud(d.getServidor(),t.getVersion(),t.getIdCuentaServicio(),false,false,parametros.obtener().getMaxDuracionTareaMinutos());t.iniciar(ahora);return new Trabajo(t.getId(),solicitud);
 }
 private void ejecutar(Integer id){
  try{
   Trabajo trabajo;while((trabajo=tx.execute(estado->siguiente(id)))!=null){final Trabajo actual=trabajo;ResultadoEjecucion respuesta;
    try{respuesta=ansible.ejecutar(actual.solicitud());}catch(RuntimeException e){respuesta=ResultadoEjecucion.fallo(false,"El adaptador no pudo completar la tarea");}
    final ResultadoEjecucion r=respuesta;tx.executeWithoutResult(estado->{ordenes.bloquearTransaccion(1263489103L);var t=tareas.findById(actual.id()).orElseThrow();t.terminar(r.codigoRetorno(),r.salida(),r.error(),Tiempo.ahora(reloj));if(!r.correcto()){var d=t.getMop().getDetalle();d.cambiarEstado(EstadoDetalleOrden.FALLIDO,Tiempo.ahora(reloj));ciclo.registrarIncidencia(d,d.isEsServidorPiloto()?OrigenIncidencia.PRUEBA_PILOTO:OrigenIncidencia.FALLO_EJECUCION,r.error()==null?"La tarea devolvió un código de error":r.error());}});
    if(!r.correcto())break;
   }
   tx.executeWithoutResult(estado->ciclo.completarEjecucion(detalles.findById(id).orElseThrow().getOrden().getId()));
  }catch(RuntimeException ex){tx.executeWithoutResult(estado->{var d=detalles.findById(id).orElseThrow();if(d.getEstado()==EstadoDetalleOrden.EN_EJECUCION)d.cambiarEstado(EstadoDetalleOrden.FALLIDO,Tiempo.ahora(reloj));ciclo.registrarIncidencia(d,OrigenIncidencia.FALLO_EJECUCION,"No se pudo completar la ejecución; revise la configuración de tareas y cuentas");});}
 }
 @PreDestroy public void cerrar(){trabajadores.shutdown();}
 private record Trabajo(Integer id,SolicitudEjecucion solicitud){}
}
