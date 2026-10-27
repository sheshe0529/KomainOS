package com.komainos.alerta.repository;

import com.komainos.alerta.model.AlertaDestinatario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AlertaDestinatarioRepositorio extends JpaRepository<AlertaDestinatario, Integer> {

    /** HU30 CA2: el filtro por destinatario va en la consulta, así la página y el total son solo suyos */
    @EntityGraph(attributePaths = {"alerta"})
    @Query("""
            select d from AlertaDestinatario d
            where d.idUsuario = :usuario and (:soloNoLeidas = false or d.leida = false)
            order by d.alerta.fechaGeneracion desc, d.id desc
            """)
    Page<AlertaDestinatario> deUsuario(@Param("usuario") Integer idUsuario,
                                       @Param("soloNoLeidas") boolean soloNoLeidas, Pageable pagina);

    @EntityGraph(attributePaths = {"alerta"})
    Optional<AlertaDestinatario> findByIdAndIdUsuario(Integer id, Integer idUsuario);

    long countByIdUsuarioAndLeidaFalse(Integer idUsuario);

    @Modifying
    @Query("""
            update AlertaDestinatario d set d.leida = true, d.fechaLectura = :ahora
            where d.idUsuario = :usuario and d.leida = false
            """)
    int leerTodas(@Param("usuario") Integer idUsuario, @Param("ahora") Instant ahora);
}
