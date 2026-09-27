package com.komainos.inventario.infra;

import com.komainos.inventario.dominio.EstadoSolicitudBaja;
import com.komainos.inventario.dominio.SolicitudBaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudBajaRepositorio extends JpaRepository<SolicitudBaja, Integer> {

    Optional<SolicitudBaja> findFirstByServidorIdAndEstado(Integer idServidor, EstadoSolicitudBaja estado);

    List<SolicitudBaja> findByServidorIdOrderByFechaSolicitudDesc(Integer idServidor);
}
