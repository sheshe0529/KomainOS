package com.komainos.alerta.repository;
import com.komainos.alerta.model.AlertaDestinatario;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.*;import java.util.Optional;
public interface AlertaDestinatarioRepositorio extends JpaRepository<AlertaDestinatario,Integer>{
 @EntityGraph(attributePaths={"alerta"}) Page<AlertaDestinatario> findByIdUsuarioOrderByAlertaFechaGeneracionDesc(Integer id,Pageable p);
 @EntityGraph(attributePaths={"alerta"}) Optional<AlertaDestinatario> findByIdAndIdUsuario(Integer id,Integer usuario);
 long countByIdUsuarioAndLeidaFalse(Integer id);
}
