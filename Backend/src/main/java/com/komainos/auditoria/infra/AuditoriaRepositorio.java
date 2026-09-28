package com.komainos.auditoria.infra;

import com.komainos.auditoria.dominio.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriaRepositorio extends JpaRepository<RegistroAuditoria, Integer> {

    List<RegistroAuditoria> findByEntidadAndIdEntidadOrderByIdAsc(String entidad, Integer idEntidad);

    List<RegistroAuditoria> findByIdServidorAndOperacionOrderByFechaHoraDescIdDesc(Integer idServidor, String operacion);
}
