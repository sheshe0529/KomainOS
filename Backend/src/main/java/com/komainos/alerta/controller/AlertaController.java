package com.komainos.alerta.controller;
import com.komainos.alerta.service.ServicioAlertas;import com.komainos.alerta.model.*;import com.komainos.seguridad.model.UsuarioAutenticado;import com.komainos.shared.dto.PaginaRespuesta;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.data.domain.Pageable;import org.springframework.data.web.PageableDefault;import java.time.Instant;import java.util.Map;
@RestController @RequestMapping("/api/alertas") @RequiredArgsConstructor
public class AlertaController {
 private final ServicioAlertas servicio;
 public record AlertaRespuesta(Integer id,TipoAlerta tipo,String titulo,String mensaje,Integer idServidor,Integer idOrden,Integer idIncidencia,Instant fecha,boolean leida,Integer idGrupo){static AlertaRespuesta de(AlertaDestinatario d){var a=d.getAlerta();return new AlertaRespuesta(d.getId(),a.getTipo(),a.getTitulo(),a.getMensaje(),a.getIdServidor(),a.getIdOrden(),a.getIdIncidencia(),a.getFechaGeneracion(),d.isLeida(),a.getIdGrupo());}}
 @GetMapping public PaginaRespuesta<AlertaRespuesta> listar(@AuthenticationPrincipal UsuarioAutenticado u,@PageableDefault(size=20)Pageable p){return PaginaRespuesta.de(servicio.listar(u.id(),p),AlertaRespuesta::de);}
 @GetMapping("/{id}")public AlertaRespuesta detalle(@PathVariable Integer id,@AuthenticationPrincipal UsuarioAutenticado u){return AlertaRespuesta.de(servicio.obtener(id,u.id()));}
 @GetMapping("/pendientes")public Map<String,Long> pendientes(@AuthenticationPrincipal UsuarioAutenticado u){return Map.of("pendientes",servicio.pendientes(u.id()));}
 @PostMapping("/{id}/lectura")@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void leer(@PathVariable Integer id,@AuthenticationPrincipal UsuarioAutenticado u){servicio.leer(id,u.id());}
}
