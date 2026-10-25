package com.komainos.mantenimiento.repository;
import com.komainos.mantenimiento.model.*;
import com.komainos.catalogo.model.NivelAfectacion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface ReglaPoliticaCambioRepositorio extends JpaRepository<ReglaPoliticaCambio,Integer> { Optional<ReglaPoliticaCambio> findByEntornoIdAndAfectacion(Integer id,NivelAfectacion nivel);
 @EntityGraph(attributePaths={"entorno"}) Page<ReglaPoliticaCambio> findAll(Pageable p); }
