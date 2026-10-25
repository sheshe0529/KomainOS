package com.komainos.mantenimiento.repository;
import com.komainos.mantenimiento.model.*;
import com.komainos.catalogo.model.NivelAfectacion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface NotificacionReglaRepositorio extends JpaRepository<NotificacionRegla,Integer> { List<NotificacionRegla> findByIdRegla(Integer id);
 void deleteByIdRegla(Integer id); }
