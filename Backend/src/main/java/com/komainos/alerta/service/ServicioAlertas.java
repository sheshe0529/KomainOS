package com.komainos.alerta.service;
import com.komainos.alerta.model.*;import com.komainos.alerta.repository.*;import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.util.Tiempo;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.data.domain.*;import java.time.Clock;import java.util.Set;
@Service @RequiredArgsConstructor
public class ServicioAlertas {
 private final AlertaRepositorio alertas;private final AlertaDestinatarioRepositorio destinatarios;private final Clock reloj;
 @Transactional public void emitir(TipoAlerta tipo,String titulo,String mensaje,Integer servidor,Integer grupo,Integer orden,Integer incidencia,Set<Integer> usuarios){
  var a=new Alerta();a.setTipo(tipo);a.setTitulo(titulo);a.setMensaje(mensaje);a.setIdServidor(servidor);a.setIdGrupo(grupo);a.setIdOrden(orden);a.setIdIncidencia(incidencia);a.setFechaGeneracion(Tiempo.ahora(reloj));alertas.save(a);
  usuarios.forEach(u->destinatarios.save(AlertaDestinatario.nuevo(a,u)));
 }
 @Transactional(readOnly=true)public Page<AlertaDestinatario> listar(Integer usuario,Pageable p){return destinatarios.findByIdUsuarioOrderByAlertaFechaGeneracionDesc(usuario,p);}
 @Transactional(readOnly=true)public long pendientes(Integer usuario){return destinatarios.countByIdUsuarioAndLeidaFalse(usuario);}
 @Transactional(readOnly=true)public AlertaDestinatario obtener(Integer id,Integer usuario){return destinatarios.findByIdAndIdUsuario(id,usuario).orElseThrow(()->RecursoNoEncontradoException.de("la alerta",id));}
 @Transactional public void leer(Integer id,Integer usuario){destinatarios.findByIdAndIdUsuario(id,usuario).orElseThrow(()->RecursoNoEncontradoException.de("la alerta",id)).leer(Tiempo.ahora(reloj));}
}
