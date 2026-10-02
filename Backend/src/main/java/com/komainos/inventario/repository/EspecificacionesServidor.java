package com.komainos.inventario.repository;

import com.komainos.inventario.model.DireccionIp;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.model.Servidor;
import com.komainos.seguridad.model.AlcanceUsuario;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** El alcance va en la consulta y no después: si no, la paginación devolvería páginas incompletas (RF11) */
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
                // Cualquiera de sus IP, no solo la principal
                Subquery<Integer> porIp = consulta.subquery(Integer.class);
                Root<DireccionIp> direccion = porIp.from(DireccionIp.class);
                porIp.select(cb.literal(1)).where(
                        cb.equal(direccion.get("servidor"), raiz),
                        cb.like(cb.lower(direccion.get("direccion")), patron));
                condiciones.add(cb.or(
                        cb.like(cb.lower(raiz.get("hostname")), patron),
                        cb.exists(porIp),
                        cb.like(cb.lower(cb.coalesce(raiz.get("dns"), "")), patron),
                        cb.like(cb.lower(cb.coalesce(raiz.get("vdc"), "")), patron),
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
            if (filtro.vdc() != null && !filtro.vdc().isBlank()) {
                condiciones.add(cb.equal(cb.lower(raiz.get("vdc")), filtro.vdc().trim().toLowerCase()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
