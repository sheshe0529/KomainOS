package com.komainos.mantenimiento.service;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;import com.komainos.inventario.model.EstadoServidor;import com.komainos.shared.model.Actor;import com.komainos.shared.util.Tiempo;
import org.springframework.stereotype.Service;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;import lombok.extern.slf4j.Slf4j;
import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
@Service @Slf4j
public class ProcesoAutomatizacion {
 private final PropiedadesAutomatizacion propiedades;private final ServicioCicloMantenimiento ciclo;private final ServicioOrquestador orquestador;private final OrdenRepositorio ordenes;private final EvaluacionRepositorio evaluaciones;private final ConfiguracionServidorRepositorio configuraciones;private final Clock reloj;private final TransactionTemplate tx;private final AtomicBoolean ocupado=new AtomicBoolean();
 public ProcesoAutomatizacion(PropiedadesAutomatizacion p,ServicioCicloMantenimiento c,ServicioOrquestador o,OrdenRepositorio ordenes,EvaluacionRepositorio e,ConfiguracionServidorRepositorio configs,Clock reloj,PlatformTransactionManager tm){propiedades=p;ciclo=c;orquestador=o;this.ordenes=ordenes;evaluaciones=e;configuraciones=configs;this.reloj=reloj;tx=new TransactionTemplate(tm);}
 @Scheduled(fixedDelayString="${komainos.automatizacion.intervalo-ms:30000}") public void programado(){if(propiedades.procesoHabilitado())procesar();}
 public int procesar(){if(!ocupado.compareAndSet(false,true))return 0;try{ciclo.vencerPlazos();
  var pendientes=tx.execute(estado->ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(Set.of(EstadoOrden.PROGRAMADA)).stream().filter(o->!o.getFechaEvaluacionProgramada().isAfter(Tiempo.ahora(reloj))).limit(50).map(Orden::getId).toList());
  for(Integer id:pendientes)try{ciclo.evaluarOrden(id,Actor.sistema("EVALUACION_PROGRAMADA"));}catch(RuntimeException e){log.warn("Evaluación de orden {} pendiente de configuración",id);}
  var revisar=tx.execute(estado->configuraciones.findAll().stream().filter(c->c.getServidor().getEstado()==EstadoServidor.ACTIVO).filter(c->evaluaciones.findFirstByServidorIdAndTipoOrderByFechaInicioDesc(c.getServidor().getId(),TipoEvaluacion.REVISION_PERIODICA).map(e->!e.getFechaInicio().plus(Duration.ofDays(c.getFrecuenciaRevisionDias())).isAfter(Tiempo.ahora(reloj))).orElse(true)).limit(50).map(c->c.getServidor().getId()).toList());
  for(Integer id:revisar)try{ciclo.revisar(id,Actor.sistema("REVISION_PERIODICA"));}catch(RuntimeException e){log.warn("Revisión del servidor {} pendiente de configuración",id);}
  return orquestador.despachar();
 }finally{ocupado.set(false);}}
}
