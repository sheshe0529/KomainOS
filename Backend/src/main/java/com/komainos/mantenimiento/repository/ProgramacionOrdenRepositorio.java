package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.ProgramacionOrden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProgramacionOrdenRepositorio extends JpaRepository<ProgramacionOrden, Integer> {

    /** Las canceladas no se muestran porque no van a ocurrir */
    @Query("""
            select p from ProgramacionOrden p
              join fetch p.orden o
              left join fetch o.servidor
              left join fetch o.grupo
              join fetch o.nivelCriticidad
            where p.numeroVersion = (select max(p2.numeroVersion) from ProgramacionOrden p2 where p2.orden = o)
              and p.fechaInicioProgramada < :hasta
              and p.fechaFinProgramada > :desde
              and o.estado <> :excluido
              and (:todos = true or exists (
                    select d.id from OrdenDetalle d
                    where d.orden = o and d.servidor.responsable.id = :idUsuario))
            order by p.fechaInicioProgramada, o.codigo
            """)
    List<ProgramacionOrden> findCronograma(@Param("desde") Instant desde,
                                           @Param("hasta") Instant hasta,
                                           @Param("excluido") EstadoOrden excluido,
                                           @Param("todos") boolean todos,
                                           @Param("idUsuario") Integer idUsuario);
}
