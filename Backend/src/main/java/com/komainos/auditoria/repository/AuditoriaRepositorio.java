package com.komainos.auditoria.repository;

import com.komainos.auditoria.model.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuditoriaRepositorio extends JpaRepository<Auditoria, Integer> {

    List<Auditoria> findByIdServidorAndOperacionOrderByFechaHoraDescIdDesc(Integer idServidor, String operacion);

    List<Auditoria> findByEntidadAndOperacionAndIdEntidadInOrderByIdAsc(String entidad, String operacion,
                                                                              Collection<Integer> idsEntidad);
}
