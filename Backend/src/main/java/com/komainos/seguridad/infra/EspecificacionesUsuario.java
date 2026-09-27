package com.komainos.seguridad.infra;

import com.komainos.seguridad.dominio.FiltroUsuarios;
import com.komainos.seguridad.dominio.Usuario;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class EspecificacionesUsuario {

    private EspecificacionesUsuario() {
    }

    public static Specification<Usuario> con(FiltroUsuarios filtro) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                String patron = "%" + filtro.texto().trim().toLowerCase() + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(raiz.get("codigo")), patron),
                        cb.like(cb.lower(raiz.get("nombreCompleto")), patron)));
            }
            if (filtro.rol() != null) {
                condiciones.add(cb.equal(raiz.get("rol"), filtro.rol()));
            }
            if (filtro.activo() != null) {
                condiciones.add(cb.equal(raiz.get("activo"), filtro.activo()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
