package com.komainos.inventario.repository;

import com.komainos.inventario.model.DireccionIp;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DireccionIpRepositorio extends JpaRepository<DireccionIp, Integer> {

    /** Direcciones ya registradas entre las indicadas, con su servidor (DEC-37: una IP, un servidor). */
    @EntityGraph(attributePaths = "servidor")
    List<DireccionIp> findByDireccionIn(Collection<String> direcciones);
}
