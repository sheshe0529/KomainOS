package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.FiltroOrdenes;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.ProgramacionOrden;
import com.komainos.seguridad.model.AlcanceUsuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros de la consulta de ordenes (RF36) y alcance del usuario. Como en el
 * inventario, el alcance va dentro de la consulta: el responsable solo obtiene
 * las ordenes en las que participa alguno de sus servidores.
 */
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
            if (f.desde() != null || f.hasta() != null) {
                // Programacion vigente: la de mayor version de la orden.
                Subquery<Integer> vigente = consulta.subquery(Integer.class);
                Root<ProgramacionOrden> p = vigente.from(ProgramacionOrden.class);
                Subquery<Integer> maxima = consulta.subquery(Integer.class);
                Root<ProgramacionOrden> p2 = maxima.from(ProgramacionOrden.class);
                maxima.select(cb.max(p2.get("numeroVersion"))).where(cb.equal(p2.get("orden"), raiz));

                List<Predicate> enRango = new ArrayList<>();
                enRango.add(cb.equal(p.get("orden"), raiz));
                enRango.add(cb.equal(p.get("numeroVersion"), maxima));
                if (f.desde() != null) {
                    enRango.add(cb.greaterThanOrEqualTo(p.get("fechaInicioProgramada"), f.desde()));
                }
                if (f.hasta() != null) {
                    enRango.add(cb.lessThan(p.get("fechaInicioProgramada"), f.hasta()));
                }
                vigente.select(p.get("id")).where(enRango.toArray(Predicate[]::new));
                condiciones.add(cb.exists(vigente));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /** Subconsulta "existe un detalle de esta orden cuyo atributo vale x". */
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
