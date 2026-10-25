package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;
import com.komainos.catalogo.model.*;import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.inventario.model.*;import com.komainos.inventario.service.*;import com.komainos.inventario.repository.*;
import com.komainos.alerta.model.TipoAlerta;import com.komainos.alerta.service.ServicioAlertas;
import com.komainos.seguridad.model.*;import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.model.Actor;import com.komainos.shared.exception.*;import com.komainos.shared.util.Tiempo;
import com.komainos.auditoria.service.ServicioAuditoria;import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class ServicioCicloMantenimiento {
 private final ServicioConsultaOrdenes consulta;private final OrdenRepositorio ordenes;private final ServicioEvaluaciones evaluaciones;private final ServicioCatalogoAutomatizacion catalogo;
 private final MopRepositorio mops;private final SolicitudAutorizacionRepositorio solicitudes;private final AutorizacionDetalleRepositorio autorizaciones;private final ValidacionFuncionalRepositorio funcionales;private final ValidacionTecnicaRepositorio tecnicas;private final IncidenciaRepositorio incidencias;
 private final ServicioPoliticasCambio politicas;private final ServicioCierreMantenimiento cierre;private final ServicioParametrosSistema parametros;private final ServicioAlertas alertas;private final UsuarioRepositorio usuarios;private final ServicioAuditoria auditoria;
 private final ServicioServidor servidores;private final ConfiguracionServidorRepositorio configuraciones;private final PuertoProgramacionCambio programacion;private final Clock reloj;
 private static final Actor SISTEMA=Actor.sistema("AUTOMATIZACION");
 @Transactional public Orden evaluarOrden(Integer id,Actor actor){
  bloquear();var o=consulta.obtener(id,new AlcanceUsuario(actor.usuarioId(),Rol.ADMINISTRADOR));
  if(o.getEstado()!=EstadoOrden.PROGRAMADA)throw new ReglaNegocioException("Solo se evalúan órdenes programadas");
  o.cambiarEstado(EstadoOrden.EN_EVALUACION,autor(actor),"Evaluación previa del mantenimiento",ahora());
  boolean falta=false;int cantidad=0;
  for(var d:o.getDetalles()){
   if(d.getEstado()==EstadoDetalleOrden.NO_INICIADO)continue;
   if(catalogo.buscarAnalisis(d.getServidor().getVersionSistemaOperativo().getId()).isEmpty()){falta=true;continue;}
   var e=evaluaciones.evaluar(d.getServidor(),d,TipoEvaluacion.PREVIA,o.getIdCuentaServicio());
   if(o.getIdCuentaServicio()==null)o.fijarCuentaServicio(e.getIdCuentaServicio());
   if(e.getEstado()==EstadoEvaluacion.FALLIDA){falta=true;continue;}
   var mop=new Mop();mop.setDetalle(d);mop.setFechaGeneracion(ahora());int posicion=0;
   for(var t:catalogo.activadas(ServicioEvaluaciones.activados(e))){var mt=new MopTarea();mt.setMop(mop);mt.setTarea(t);mt.setOrdenEjecucion(++posicion);mt.setPrioridadAplicada(t.getPrioridad());mt.setDuracionEstimadaMinutos(t.getDuracionEstimadaMinutos());mt.fijarAfectacion(t.getNivelAfectacion());mt.setIdCuentaServicio(o.getIdCuentaServicio()!=null?o.getIdCuentaServicio():e.getIdCuentaServicio());mt.setVersion(catalogo.compatible(t.getScript().getId(),d.getServidor().getVersionSistemaOperativo().getId()).orElse(null));falta|=mt.getVersion()==null;mop.getTareas().add(mt);}
   mops.save(mop);if(mop.getTareas().isEmpty())d.omitirSiPendiente();cantidad+=mop.getTareas().size();
   if(mop.duracionTotal()>parametros.obtener().getMaxDuracionMopMinutos())auditoria.registrar(actor,Operacion.de("ADVERTENCIA_DURACION_MOP","mop",mop.getId()).sobreOrden(id).motivo("La duración estimada supera el máximo por MOP"));
  }
  if(falta){o.cambiarEstado(EstadoOrden.SIN_IMPLEMENTACION,autor(actor),"Análisis fallido o implementación compatible no disponible",ahora());notificar(o,TipoAlerta.SIN_IMPLEMENTACION,"Mantenimiento bloqueado","Revise el análisis y las implementaciones compatibles.");return o;}
  if(cantidad==0){cierre.cerrar(o,"La evaluación no requiere intervención",actor);return o;}
  if(o.esGrupal()&&o.getModoEjecucionAplicado()==ModoEjecucion.PARALELO){mops.findByDetalleOrdenId(id).stream().max(Comparator.comparingInt((Mop m)->m.getTareas().size()).thenComparing(m->-m.getDetalle().getPosicionEjecucion())).ifPresent(m->m.getDetalle().marcarPiloto());}
  solicitar(o,actor);return o;
 }
 private void solicitar(Orden o,Actor actor){
  NivelAfectacion afectacion=mops.findByDetalleOrdenId(o.getId()).stream().flatMap(m->m.getTareas().stream()).map(MopTarea::getNivelAfectacion).reduce(NivelAfectacion.SIN_AFECTACION,NivelAfectacion::mayor);
  var regla=politicas.aplicable(o.getDetalles().getFirst().getServidor().getEntorno().getId(),afectacion);
  var solicitud=new SolicitudAutorizacion();solicitud.setOrden(o);solicitud.setRegla(regla);solicitud.setNumeroRonda(solicitudes.findFirstByOrdenIdOrderByNumeroRondaDesc(o.getId()).map(s->s.getNumeroRonda()+1).orElse(1));
  Instant limite=o.getOrigen()==OrigenOrden.LANZAMIENTO_INMEDIATO||o.getOrigen()==OrigenOrden.CONDICION_CRITICA?ahora().plus(Duration.ofHours(o.getNivelCriticidad().getPlazoAutorizacionHoras())):o.getFechaInicioProgramada();solicitud.setFechaLimite(limite);
  if(regla.isRequiereResponsable())agregarDecision(solicitud,TipoAprobador.RESPONSABLE);
  if(regla.isRequiereAdministrador())agregarDecision(solicitud,TipoAprobador.ADMINISTRADOR);
  solicitudes.save(solicitud);
  if(solicitud.getDecisiones().isEmpty()){if(o.getEstado()!=EstadoOrden.AUTORIZADA)o.cambiarEstado(EstadoOrden.AUTORIZADA,autor(actor),"Autorización automática según política de cambio",ahora());}
  else{if(o.getEstado()!=EstadoOrden.PENDIENTE_AUTORIZACION)o.cambiarEstado(EstadoOrden.PENDIENTE_AUTORIZACION,autor(actor),"Se requieren aprobaciones según política de cambio",ahora());notificarAprobador(o,solicitud.getDecisiones().getFirst().getTipo());}
  for(var n:politicas.notificaciones(regla.getId()))alertas.emitir(TipoAlerta.SOLICITUD_AUTORIZACION,"Política de cambio aplicada",o.getCodigo()+": revisión del mantenimiento",null,o.esGrupal()?o.getGrupo().getId():null,o.getId(),null,destinatarios(o,n.getDestinatario()));
 }
 private void agregarDecision(SolicitudAutorizacion s,TipoAprobador tipo){var a=new AutorizacionDetalle();a.setSolicitud(s);a.definirTipo(tipo);a.setOrdenSecuencia(s.getDecisiones().size()+1);a.setFechaSolicitud(ahora());s.getDecisiones().add(a);}
 /** RF44/RF75: solo la primera aprobación pendiente puede responder, todas comparten la misma fecha límite. */
 @Transactional public Orden decidir(Integer id,EstadoAutorizacion decision,String motivo,Instant propuesta,AlcanceUsuario alcance){
  bloquear();var o=consulta.obtener(id,alcance);if(o.getEstado()!=EstadoOrden.PENDIENTE_AUTORIZACION)throw new ReglaNegocioException("La orden no está pendiente de autorización");
  var s=solicitudes.findFirstByOrdenIdOrderByNumeroRondaDesc(id).orElseThrow();if(!ahora().isBefore(s.getFechaLimite()))throw new ReglaNegocioException("El plazo compartido de autorización venció");
  var a=s.getDecisiones().stream().filter(x->x.getEstado()==EstadoAutorizacion.PENDIENTE).findFirst().orElseThrow(()->new ReglaNegocioException("No quedan decisiones pendientes"));
  if(a.getTipo()==TipoAprobador.ADMINISTRADOR&&!alcance.esAdministrador()||a.getTipo()==TipoAprobador.RESPONSABLE&&(alcance.rol()!=Rol.RESPONSABLE||o.getDetalles().stream().anyMatch(d->!d.getServidor().esVisiblePara(alcance.usuarioId()))))throw new ReglaNegocioException("Todavía no corresponde la aprobación de su rol");
  if(!Set.of(EstadoAutorizacion.APROBADA,EstadoAutorizacion.RECHAZADA,EstadoAutorizacion.FECHA_PROPUESTA).contains(decision))throw new ReglaNegocioException("Decisión no permitida");
  if(decision==EstadoAutorizacion.FECHA_PROPUESTA&&(a.getTipo()!=TipoAprobador.RESPONSABLE||propuesta==null||!propuesta.isAfter(ahora())))throw new ReglaNegocioException("Indique una fecha futura para proponer");
  a.decidir(decision,autor(alcance.actor()),motivo,propuesta,ahora());
  auditoria.registrar(alcance.actor(),Operacion.de("DECIDIR_AUTORIZACION","orden",id).sobreOrden(id).motivo(decision+": "+motivo));
  if(decision==EstadoAutorizacion.RECHAZADA){o.cambiarEstado(EstadoOrden.RECHAZADA,autor(alcance.actor()),motivo,ahora());cierre.cerrar(o,"Mantenimiento rechazado: "+motivo,alcance.actor());}
  else if(decision==EstadoAutorizacion.FECHA_PROPUESTA){notificarAprobador(o,TipoAprobador.ADMINISTRADOR);}
  else if(s.getDecisiones().stream().allMatch(x->x.getEstado()==EstadoAutorizacion.APROBADA)){o.cambiarEstado(EstadoOrden.AUTORIZADA,autor(alcance.actor()),"Aprobaciones completas",ahora());}
  else{s.getDecisiones().stream().filter(x->x.getEstado()==EstadoAutorizacion.PENDIENTE).findFirst().ifPresent(x->{x.setFechaSolicitud(ahora());notificarAprobador(o,x.getTipo());});}return o;
 }
 @Transactional public void aceptarFecha(Integer id,Actor actor){
  bloquear();var o=consulta.obtener(id,new AlcanceUsuario(actor.usuarioId(),Rol.ADMINISTRADOR));if(o.getEstado()!=EstadoOrden.PENDIENTE_AUTORIZACION)throw new ReglaNegocioException("La orden no está en autorización");var s=solicitudes.findFirstByOrdenIdOrderByNumeroRondaDesc(id).orElseThrow();var propuesta=s.getDecisiones().stream().filter(a->a.getEstado()==EstadoAutorizacion.FECHA_PROPUESTA).findFirst().orElseThrow(()->new ReglaNegocioException("No hay fecha propuesta"));
  programacion.reprogramarAutorizacion(id,propuesta.getFechaPropuesta(),"Fecha propuesta por el responsable: "+propuesta.getMotivo(),actor);solicitar(o,actor);
 }
 @Transactional public Evaluacion revisar(Integer id,Actor actor){bloquear();var servidor=servidores.obtenerSinAlcance(id);if(servidor.getEstado()!=EstadoServidor.ACTIVO)throw new ReglaNegocioException("Solo se revisan servidores activos");var config=configuraciones.findByServidorId(id).orElseThrow();var e=evaluaciones.evaluar(servidor,null,TipoEvaluacion.REVISION_PERIODICA,config.getIdCuentaServicio());
  var nivel=ServicioEvaluaciones.nivel(e);if(e.getEstado()==EstadoEvaluacion.FALLIDA){alertas.emitir(TipoAlerta.INCIDENCIA_REGISTRADA,"Revisión periódica fallida",e.getError(),id,null,null,null,usuariosDelServidor(servidor));}
  else if(nivel!=NivelAlerta.NORMAL){alertas.emitir(nivel==NivelAlerta.CRITICO?TipoAlerta.CONDICION_CRITICA:TipoAlerta.ADVERTENCIA_UMBRAL,"Salud del servidor: "+nivel,"La revisión periódica requiere atención",id,null,null,null,usuariosDelServidor(servidor));
   if(nivel==NivelAlerta.CRITICO&&!ordenes.existeDelServidorEnEstados(id,EstadoOrden.queOcupanCronograma())){var o=programacion.inmediata(id,null,false,"Condición crítica detectada en revisión periódica",e.getId(),actor);evaluarOrden(o.getId(),actor);}
  }return e;
 }
 @Transactional public void completarEjecucion(Integer id){
  bloquear();
  var o=consulta.obtener(id,new AlcanceUsuario(null,Rol.ADMINISTRADOR));
  if(o.getDetalles().stream().anyMatch(d->d.getEstado()==EstadoDetalleOrden.EN_EJECUCION||d.getEstado()==EstadoDetalleOrden.EN_COLA||d.getEstado()==EstadoDetalleOrden.PENDIENTE))return;
  if(o.getEstado()==EstadoOrden.INCIDENCIA){
   var casos=incidencias.findByDetalleOrdenId(id);
   if(!casos.isEmpty()&&casos.stream().allMatch(i->i.getEstado()==EstadoIncidencia.CERRADA))cierre.cerrar(o,"Incidencias atendidas y tareas en curso finalizadas",SISTEMA);
   return;
  }
  if(o.getEstado()!=EstadoOrden.EN_EJECUCION)return;
  for(var d:o.getDetalles())if(d.getEstado()==EstadoDetalleOrden.FINALIZADO){var e=evaluaciones.evaluar(d.getServidor(),d,TipoEvaluacion.POSTERIOR,o.getIdCuentaServicio());boolean conforme=e.getEstado()==EstadoEvaluacion.FINALIZADA&&ServicioEvaluaciones.activados(e).isEmpty();var v=new ValidacionTecnica();v.setDetalle(d);v.definirResultado(conforme?ResultadoValidacionTecnica.CONFORME:ResultadoValidacionTecnica.NO_CONFORME);v.setFechaValidacion(ahora());v.setObservaciones(conforme?"Métricas dentro de los umbrales":"Persisten umbrales activados o falló el análisis posterior");tecnicas.save(v);if(!conforme)registrarIncidencia(d,OrigenIncidencia.VALIDACION_TECNICA_NO_CONFORME,v.getObservaciones());}
  if(o.getEstado()==EstadoOrden.INCIDENCIA)return;
  o.cambiarEstado(EstadoOrden.PENDIENTE_VALIDACION,null,"Validación técnica conforme; pendiente de conformidad funcional",ahora());
  Map<Integer,Usuario> responsables=new LinkedHashMap<>();o.getDetalles().stream().filter(d->d.getEstado()==EstadoDetalleOrden.FINALIZADO).forEach(d->responsables.put(d.getServidor().getResponsable().getId(),d.getServidor().getResponsable()));
  for(var responsable:responsables.values()){var v=new ValidacionFuncional();v.setOrden(o);v.setResponsable(responsable);v.setFechaSolicitud(ahora());v.setFechaLimite(ahora().plus(Duration.ofHours(o.getNivelCriticidad().getPlazoValidacionHoras())));funcionales.save(v);alertas.emitir(TipoAlerta.SOLICITUD_VALIDACION,"Validación funcional pendiente",o.getCodigo()+": revise el reporte comparativo",null,null,id,null,Set.of(responsable.getId()));}
 }
 @Transactional public void validar(Integer id,boolean conforme,String observaciones,AlcanceUsuario alcance){
  bloquear();var o=consulta.obtener(id,alcance);if(o.getEstado()!=EstadoOrden.PENDIENTE_VALIDACION)throw new ReglaNegocioException("La orden no espera validación funcional");
  var v=funcionales.findByOrdenId(id).stream().filter(x->x.getResponsable().getId().equals(alcance.usuarioId())).findFirst().orElseThrow(()->new ReglaNegocioException("La validación corresponde al responsable asignado"));
  if(!ahora().isBefore(v.getFechaLimite()))throw new ReglaNegocioException("El plazo de validación venció");v.responder(conforme?EstadoValidacionFuncional.CONFORME:EstadoValidacionFuncional.RECHAZADA,observaciones,ahora());
  auditoria.registrar(alcance.actor(),Operacion.de("VALIDAR_MANTENIMIENTO","orden",id).sobreOrden(id).motivo(observaciones));
  if(!conforme){o.getDetalles().stream().filter(d->d.getServidor().esVisiblePara(alcance.usuarioId())&&d.getEstado()==EstadoDetalleOrden.FINALIZADO).forEach(d->registrarIncidencia(d,OrigenIncidencia.RECHAZO_FUNCIONAL,observaciones));}
  else if(funcionales.findByOrdenId(id).stream().allMatch(x->x.getEstado()==EstadoValidacionFuncional.CONFORME)){notificar(o,TipoAlerta.CIERRE_MANTENIMIENTO,"Mantenimiento validado",observaciones);cierre.cerrar(o,"Validaciones técnica y funcional conformes",alcance.actor());}
 }
 @Transactional public Incidencia registrarIncidencia(OrdenDetalle d,OrigenIncidencia origen,String error){
  var i=new Incidencia();i.setDetalle(d);i.definirOrigen(origen);i.setError(error);i.setFechaApertura(ahora());incidencias.save(i);var o=d.getOrden();if(o.getEstado().puedePasarA(EstadoOrden.INCIDENCIA))o.cambiarEstado(EstadoOrden.INCIDENCIA,null,error,ahora());
  for(var otro:o.getDetalles())if(otro.getEstado()==EstadoDetalleOrden.EN_COLA||otro.getEstado()==EstadoDetalleOrden.PENDIENTE)otro.omitirSiPendiente();
  for(var m:mops.findByDetalleOrdenId(o.getId()))m.getTareas().forEach(MopTarea::omitir);
  alertas.emitir(origen==OrigenIncidencia.TIEMPO_EXCEDIDO?TipoAlerta.VENTANA_EXCEDIDA:TipoAlerta.INCIDENCIA_REGISTRADA,"Incidencia en "+o.getCodigo(),error,d.getServidor().getId(),o.esGrupal()?o.getGrupo().getId():null,o.getId(),i.getId(),usuariosDelServidor(d.getServidor()));return i;
 }
 @Transactional public Incidencia atender(Integer id,String diagnostico,String acciones,String resultado,boolean cerrarIncidencia,Actor actor){bloquear();var i=incidencias.findById(id).orElseThrow(()->RecursoNoEncontradoException.de("la incidencia",id));i.atender(autor(actor),diagnostico,acciones,resultado,cerrarIncidencia,ahora());var o=i.getDetalle().getOrden();auditoria.registrar(actor,Operacion.de("ATENDER_INCIDENCIA","incidencia",id).sobreOrden(o.getId()).motivo(resultado));
  org.hibernate.Hibernate.initialize(i.getDetalle().getServidor());
  if(cerrarIncidencia&&incidencias.findByDetalleOrdenId(o.getId()).stream().allMatch(x->x.getEstado()==EstadoIncidencia.CERRADA)&&o.getDetalles().stream().noneMatch(d->d.getEstado()==EstadoDetalleOrden.EN_EJECUCION))cierre.cerrar(o,"Incidencias atendidas: "+resultado,actor);return i;
 }
 @Transactional public void cerrarSinImplementacion(Integer id,String motivo,Actor actor){bloquear();var o=consulta.obtener(id,new AlcanceUsuario(actor.usuarioId(),Rol.ADMINISTRADOR));if(o.getEstado()!=EstadoOrden.SIN_IMPLEMENTACION)throw new ReglaNegocioException("Esta acción solo cierra órdenes sin implementación");cierre.cerrar(o,motivo,actor);}
 @Transactional public void vencerPlazos(){bloquear();for(var o:ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(Set.of(EstadoOrden.PENDIENTE_AUTORIZACION,EstadoOrden.PENDIENTE_VALIDACION,EstadoOrden.CANCELADA))){
  if(o.getEstado()==EstadoOrden.CANCELADA){cierre.cerrar(o,"Programación cancelada",SISTEMA);continue;}
  if(o.getEstado()==EstadoOrden.PENDIENTE_AUTORIZACION){var s=solicitudes.findFirstByOrdenIdOrderByNumeroRondaDesc(o.getId()).orElseThrow();if(!ahora().isBefore(s.getFechaLimite())){s.getDecisiones().stream().filter(a->a.getEstado()==EstadoAutorizacion.PENDIENTE).forEach(a->a.decidir(EstadoAutorizacion.VENCIDA,null,"Plazo compartido vencido",null,ahora()));o.cambiarEstado(EstadoOrden.AUTORIZACION_VENCIDA,null,"Plazo compartido vencido",ahora());notificar(o,TipoAlerta.AUTORIZACION_VENCIDA,"Autorización vencida","La orden se cierra sin ejecutar");cierre.cerrar(o,"Autorizaciones vencidas",SISTEMA);}}
  else {var validaciones=funcionales.findByOrdenId(o.getId());if(validaciones.stream().anyMatch(v->v.getEstado()==EstadoValidacionFuncional.PENDIENTE&&!ahora().isBefore(v.getFechaLimite()))){validaciones.stream().filter(v->v.getEstado()==EstadoValidacionFuncional.PENDIENTE).forEach(v->v.responder(EstadoValidacionFuncional.VENCIDA,"Sin respuesta dentro del plazo",ahora()));o.cambiarEstado(EstadoOrden.VALIDACION_VENCIDA,null,"Plazo de validación vencido; no se presume conformidad",ahora());notificar(o,TipoAlerta.VALIDACION_VENCIDA,"Validación vencida","Se cierra sin asumir conformidad funcional");cierre.cerrar(o,"Validación funcional vencida",SISTEMA);}}
 }}
 public Set<Integer> usuariosDelServidor(Servidor s){Set<Integer> ids=new HashSet<>();usuarios.findAll().stream().filter(u->u.isActivo()&&u.getRol()==Rol.ADMINISTRADOR).forEach(u->ids.add(u.getId()));if(s.getResponsable()!=null)ids.add(s.getResponsable().getId());return ids;}
 private Set<Integer> destinatarios(Orden o,TipoDestinatario tipo){Set<Integer> ids=new HashSet<>();if(tipo==TipoDestinatario.RESPONSABLE)o.getDetalles().forEach(d->ids.add(d.getServidor().getResponsable().getId()));else usuarios.findAll().stream().filter(u->u.isActivo()&&u.getRol().name().equals(tipo.name())).forEach(u->ids.add(u.getId()));return ids;}
 private void notificarAprobador(Orden o,TipoAprobador tipo){alertas.emitir(TipoAlerta.SOLICITUD_AUTORIZACION,"Aprobación de "+tipo+" requerida",o.getCodigo()+": revise las tareas y el plazo compartido",null,null,o.getId(),null,destinatarios(o,TipoDestinatario.valueOf(tipo.name())));}
 private void notificar(Orden o,TipoAlerta tipo,String titulo,String mensaje){Set<Integer> ids=new HashSet<>();o.getDetalles().forEach(d->ids.addAll(usuariosDelServidor(d.getServidor())));alertas.emitir(tipo,titulo,mensaje,null,o.esGrupal()?o.getGrupo().getId():null,o.getId(),null,ids);}
 private Instant ahora(){return Tiempo.ahora(reloj);} private Usuario autor(Actor a){return a.esSistema()?null:usuarios.findById(a.usuarioId()).orElseThrow();}private void bloquear(){ordenes.bloquearTransaccion(1263489102L);}
}
