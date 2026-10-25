package com.komainos.seguridad.repository;

import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Integer>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByCodigo(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByRolAndActivoTrue(Rol rol);

    /** FOR UPDATE: dos cambios simultáneos sobre administradores se serializan y el segundo ve el resultado del primero */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.rol = :rol and u.activo = true order by u.id")
    List<Usuario> bloquearActivosConRol(@Param("rol") Rol rol);
}
