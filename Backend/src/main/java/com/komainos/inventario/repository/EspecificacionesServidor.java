package com.komainos.inventario.repository;

import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.model.Servidor;
import com.komainos.seguridad.model.AlcanceUsuario;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Traduce los filtros del inventario y el alcance del usuario a una consulta.
 *
 * <p>El alcance se aplica aqui, junto al resto de condiciones, y no filtrando
 * la lista ya cargada: si se filtrara despues, la paginacion devolveria paginas
 * incompletas al responsable y un total que no le corresponde (RF11).
 */
public final class EspecificacionesServidor {

    private EspecificacionesServidor() {
    }

    public static Specification<Servidor> con(FiltroServidores filtro, AlcanceUsuario alcance) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (!alcance.veTodoElInventario()) {
                condiciones.add(cb.equal(raiz.get("responsable").get("id"), alcance.usuarioId()));
            }

            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                String patron = "%" + filtro.texto().trim().toLowerCase() + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(raiz.get("hostname")), patron),
                        cb.like(cb.lower(raiz.get("direccionIp")), patron),
                        cb.like(cb.lower(cb.coalesce(raiz.get("dns"), "")), patron),
                        cb.like(cb.lower(cb.coalesce(raiz.get("datacenter"), "")), patron),
                        cb.like(cb.lower(raiz.get("responsable").get("nombreCompleto")), patron)));
            }
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(raiz.get("estado"), filtro.estado()));
            }
            if (filtro.idEntorno() != null) {
                condiciones.add(cb.equal(raiz.get("entorno").get("id"), filtro.idEntorno()));
            }
            if (filtro.idNivelCriticidad() != null) {
                condiciones.add(cb.equal(raiz.get("nivelCriticidad").get("id"), filtro.idNivelCriticidad()));
            }
            if (filtro.idSistemaOperativo() != null) {
                condiciones.add(cb.equal(
                        raiz.get("versionSistemaOperativo").get("sistemaOperativo").get("id"),
                        filtro.idSistemaOperativo()));
            }
            if (filtro.idResponsable() != null) {
                condiciones.add(cb.equal(raiz.get("responsable").get("id"), filtro.idResponsable()));
            }
            if (filtro.datacenter() != null && !filtro.datacenter().isBlank()) {
                condiciones.add(cb.equal(cb.lower(raiz.get("datacenter")), filtro.datacenter().trim().toLowerCase()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
