package com.komainos.mantenimiento.service;

import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.mantenimiento.model.FiltroOrdenes;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.repository.EspecificacionesOrden;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServicioConsultaOrdenes {

    private final OrdenRepositorio ordenes;
    private final PuertoCuentasServicio cuentas;

    @Transactional(readOnly = true)
    public Page<Orden> listar(FiltroOrdenes filtro, AlcanceUsuario alcance, Pageable paginacion) {
        Page<Orden> pagina = ordenes.findAll(EspecificacionesOrden.con(filtro, alcance), paginacion);
        // Con BatchSize estas inicializaciones cargan las colecciones de toda la página en pocas consultas
        pagina.forEach(o -> {
            Hibernate.initialize(o.getProgramaciones());
            Hibernate.initialize(o.getDetalles());
        });
        return pagina;
    }

    @Transactional(readOnly = true)
    public Orden obtener(Integer id, AlcanceUsuario alcance) {
        Orden orden = ordenes.findConDetalleById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la orden", id));
        if (!alcance.veTodoElInventario() && orden.getDetalles().stream()
                .noneMatch(d -> d.getServidor().esVisiblePara(alcance.usuarioId()))) {
            throw RecursoNoEncontradoException.de("la orden", id);
        }
        orden.getProgramaciones().forEach(p -> Hibernate.initialize(p.getUsuarioRegistro()));
        orden.getHistorial().forEach(h -> Hibernate.initialize(h.getUsuario()));
        return orden;
    }

    /** La cuenta es la que se resolvió al generar la orden: cambios posteriores de la configuración no la alteran */
    @Transactional(readOnly = true)
    public DetalleOrden detalle(Integer id, AlcanceUsuario alcance) {
        Orden orden = obtener(id, alcance);
        Optional<String> cuenta = orden.getIdCuentaServicio() == null
                ? Optional.empty() : cuentas.nombre(orden.getIdCuentaServicio());
        return new DetalleOrden(orden, cuenta);
    }

    public record DetalleOrden(Orden orden, Optional<String> cuentaServicio) {
    }
}
