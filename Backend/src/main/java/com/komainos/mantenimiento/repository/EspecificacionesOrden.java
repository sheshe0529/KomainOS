package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.FiltroOrdenes;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.seguridad.model.AlcanceUsuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class EspecificacionesOrden {

    private EspecificacionesOrden() {
    }

    public static Specification<Orden> con(FiltroOrdenes f, AlcanceUsuario alcance) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (!alcance.veTodoElInventario()) {
                condiciones.add(cb.exists(detalleDe(raiz, consulta.subquery(Integer.class), cb,
                        "servidor.responsable.id", alcance.usuarioId())));
            }
            if (f.codigo() != null && !f.codigo().isBlank()) {
                condiciones.add(cb.like(cb.lower(raiz.get("codigo")), "%" + f.codigo().trim().toLowerCase() + "%"));
            }
            if (f.idServidor() != null) {
                condiciones.add(cb.exists(detalleDe(raiz, consulta.subquery(Integer.class), cb,
                        "servidor.id", f.idServidor())));
            }
            if (f.idGrupo() != null) {
                condiciones.add(cb.equal(raiz.get("grupo").get("id"), f.idGrupo()));
            }
            if (f.estado() != null) {
                condiciones.add(cb.equal(raiz.get("estado"), f.estado()));
            }
            if (f.idNivelCriticidad() != null) {
                condiciones.add(cb.equal(raiz.get("nivelCriticidad").get("id"), f.idNivelCriticidad()));
            }
            if (f.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaInicioProgramada"), f.desde()));
            }
            if (f.hasta() != null) {
                condiciones.add(cb.lessThan(raiz.get("fechaInicioProgramada"), f.hasta()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private static Subquery<Integer> detalleDe(Root<Orden> orden, Subquery<Integer> sub, CriteriaBuilder cb,
                                               String ruta, Integer valor) {
        Root<OrdenDetalle> d = sub.from(OrdenDetalle.class);
        jakarta.persistence.criteria.Path<Object> campo = d.get(ruta.split("\\.")[0]);
        String[] partes = ruta.split("\\.");
        for (int i = 1; i < partes.length; i++) {
            campo = campo.get(partes[i]);
        }
        return sub.select(d.get("id")).where(cb.equal(d.get("orden"), orden), cb.equal(campo, valor));
    }
}
