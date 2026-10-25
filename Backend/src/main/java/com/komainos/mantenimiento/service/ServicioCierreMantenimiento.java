package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;import com.komainos.mantenimiento.event.CicloMantenimientoCerrado;import com.komainos.inventario.repository.*;import com.komainos.inventario.service.*;import com.komainos.inventario.model.NivelAlerta;import com.komainos.shared.model.Actor;import com.komainos.shared.util.Tiempo;import com.komainos.auditoria.service.ServicioAuditoria;import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.context.ApplicationEventPublisher;import org.springframework.data.domain.PageRequest;import java.time.Clock;import java.util.*;
@Service @RequiredArgsConstructor
public class ServicioCierreMantenimiento {
 private final CierreOrdenRepositorio cierres;private final IncidenciaRepositorio incidencias;private final ValidacionFuncionalRepositorio validaciones;private final EvaluacionRepositorio evaluaciones;private final ServicioParametrosSistema parametros;private final ConfiguracionServidorRepositorio cs;private final ConfiguracionGrupoRepositorio cg;private final ServicioServidor servidores;private final ServicioAuditoria auditoria;private final ApplicationEventPublisher eventos;private final Clock reloj;
 @Transactional public void cerrar(Orden orden,String motivo,Actor actor){
  if(orden.getEstado()==EstadoOrden.CERRADA)return;
  boolean incidencia=!incidencias.findByDetalleOrdenId(orden.getId()).isEmpty();
  boolean conforme=!validaciones.findByOrdenId(orden.getId()).isEmpty()&&validaciones.findByOrdenId(orden.getId()).stream().allMatch(v->v.getEstado()==EstadoValidacionFuncional.CONFORME);
  NivelAlerta peor=NivelAlerta.NORMAL;for(var d:orden.getDetalles()){var historial=evaluaciones.findByDetalleIdOrderByFechaInicioAsc(d.getId());var ultima=historial.stream().filter(e->e.getTipo()==TipoEvaluacion.POSTERIOR).reduce((a,z)->z).or(()->historial.stream().reduce((a,z)->z));if(ultima.isPresent())peor=peor.peor(ServicioEvaluaciones.nivel(ultima.get()));}
  int min=parametros.obtener().getMinCiclosRachaEstable();int racha=0;
  if(conforme&&!incidencia){for(var c:cierres.ultimos(orden.esGrupal()?null:orden.getServidor().getId(),orden.esGrupal()?orden.getGrupo().getId():null,PageRequest.of(0,min))){if(incidencias.findByDetalleOrdenId(c.getOrden().getId()).isEmpty()&&!validaciones.findByOrdenId(c.getOrden().getId()).isEmpty()&&validaciones.findByOrdenId(c.getOrden().getId()).stream().allMatch(v->v.getEstado()==EstadoValidacionFuncional.CONFORME))racha++;else break;}}
  String resultado=resultado(incidencia,conforme,peor,racha+1,min);var f=cierres.factor(resultado);int periodo=orden.esGrupal()?cg.findByGrupoId(orden.getGrupo().getId()).map(c->c.getFrecuenciaMantenimientoDias()).orElse(orden.getNivelCriticidad().getFrecuenciaMantenimientoDias()):cs.findByServidorId(orden.getServidor().getId()).map(c->c.getFrecuenciaMantenimientoDias()).orElse(orden.getNivelCriticidad().getFrecuenciaMantenimientoDias());
  orden.getDetalles().forEach(OrdenDetalle::omitirSiPendiente);orden.cambiarEstado(EstadoOrden.CERRADA,null,motivo,Tiempo.ahora(reloj));
  cierres.save(CierreOrden.nuevo(orden,f.getId(),periodo,f.getFactor(),Tiempo.ahora(reloj),motivo));
  auditoria.registrar(actor,Operacion.de("CERRAR_MANTENIMIENTO","orden",orden.getId()).sobreOrden(orden.getId()).motivo(motivo));
  orden.getDetalles().forEach(d->servidores.aplicarBajaPendiente(d.getServidor().getId()));eventos.publishEvent(new CicloMantenimientoCerrado(orden.getId(),actor));
 }
 public static String resultado(boolean incidencia,boolean conforme,NivelAlerta nivel,int racha,int minimo){if(incidencia)return "INCIDENCIA";if(nivel!=NivelAlerta.NORMAL)return "SALUD_REGULAR";return conforme&&racha>=minimo?"RACHA_ESTABLE":"ESTADO_NORMAL";}
}
