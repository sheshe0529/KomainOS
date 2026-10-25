package com.komainos.mantenimiento.service;
import com.komainos.mantenimiento.model.*;import com.komainos.mantenimiento.repository.*;import com.komainos.catalogo.model.NivelAfectacion;import com.komainos.inventario.service.ServicioCatalogos;import com.komainos.auditoria.service.ServicioAuditoria;import com.komainos.auditoria.service.ServicioAuditoria.Operacion;import com.komainos.shared.model.Actor;import com.komainos.shared.util.*;import com.komainos.shared.exception.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.data.domain.*;import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class ServicioPoliticasCambio {
 private final ReglaPoliticaCambioRepositorio reglas;private final NotificacionReglaRepositorio notificaciones;private final ServicioCatalogos catalogos;private final ServicioAuditoria auditoria;private final Clock reloj;
 @Transactional(readOnly=true) public Page<ReglaPoliticaCambio> listar(Pageable p){return reglas.findAll(p);}
 @Transactional(readOnly=true) public List<NotificacionRegla> notificaciones(Integer id){return notificaciones.findByIdRegla(id);}
 @Transactional public ReglaPoliticaCambio guardar(Integer id,DatosRegla datos,Actor actor){
  var entorno=catalogos.obtenerEntorno(datos.idEntorno());if(!entorno.isActivo())throw new ReglaNegocioException("El entorno está inactivo");
  var existente=reglas.findByEntornoIdAndAfectacion(datos.idEntorno(),datos.afectacion());if(existente.isPresent()&&!Objects.equals(existente.get().getId(),id))throw new ConflictoException("Ya hay una regla para ese entorno y afectación");
  var r=id==null?new ReglaPoliticaCambio():reglas.findById(id).orElseThrow(()->RecursoNoEncontradoException.de("la regla",id));
  Concurrencia.exigirVersion(datos.version(),r.getFechaActualizacion(),"la política");r.setEntorno(entorno);r.definirAfectacion(datos.afectacion());r.setRequiereResponsable(datos.responsable());r.setRequiereAdministrador(datos.administrador());r.setActivo(datos.activo());r.setFechaActualizacion(Tiempo.ahora(reloj));reglas.save(r);
  notificaciones.deleteByIdRegla(r.getId());notificaciones.flush();for(var tipo:datos.notificaciones()){var n=new NotificacionRegla();n.setIdRegla(r.getId());n.definirDestinatario(tipo);notificaciones.save(n);}
  auditoria.registrar(actor,Operacion.de("GUARDAR_POLITICA_CAMBIO","regla_politica_cambio",r.getId()));return r;
 }
 @Transactional(readOnly=true)public ReglaPoliticaCambio aplicable(Integer entorno,NivelAfectacion nivel){return reglas.findByEntornoIdAndAfectacion(entorno,nivel).filter(ReglaPoliticaCambio::isActivo).orElseThrow(()->new ReglaNegocioException("Complete la política de cambio para este entorno y afectación antes de autorizar"));}
 public record DatosRegla(Integer idEntorno,NivelAfectacion afectacion,boolean responsable,boolean administrador,boolean activo,Set<TipoDestinatario> notificaciones,Instant version){}
}
