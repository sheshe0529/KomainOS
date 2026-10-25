package com.komainos.mantenimiento.dto;
import com.komainos.catalogo.model.NivelAfectacion;import com.komainos.mantenimiento.model.*;import java.time.Instant;import java.util.List;
public record PoliticaCambioRespuesta(Integer id,Integer idEntorno,String entorno,NivelAfectacion nivelAfectacion,boolean requiereResponsable,boolean requiereAdministrador,boolean activo,Instant fechaActualizacion,List<TipoDestinatario> notificaciones){
 public static PoliticaCambioRespuesta de(ReglaPoliticaCambio r,List<NotificacionRegla> n){return new PoliticaCambioRespuesta(r.getId(),r.getEntorno().getId(),r.getEntorno().getNombre(),r.getAfectacion(),r.isRequiereResponsable(),r.isRequiereAdministrador(),r.isActivo(),r.getFechaActualizacion(),n.stream().map(NotificacionRegla::getDestinatario).toList());}
}
