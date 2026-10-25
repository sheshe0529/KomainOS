package com.komainos.mantenimiento.repository;
import com.komainos.mantenimiento.model.*;
import com.komainos.catalogo.model.NivelAfectacion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface ValidacionFuncionalRepositorio extends JpaRepository<ValidacionFuncional,Integer> { List<ValidacionFuncional> findByOrdenId(Integer id); }
