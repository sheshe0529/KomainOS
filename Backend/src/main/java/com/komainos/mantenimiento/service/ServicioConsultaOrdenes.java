package com.komainos.mantenimiento.service;

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

/**
 * Consulta de ordenes y su detalle (RF33, RF36, HU23). Devuelve las entidades
 * con las colecciones que la API muestra ya inicializadas, porque la sesion
 * JPA se cierra al salir de aqui.
 */
@Service
@RequiredArgsConstructor
public class ServicioConsultaOrdenes {

    private final OrdenRepositorio ordenes;

    @Transactional(readOnly = true)
    public Page<Orden> listar(FiltroOrdenes filtro, AlcanceUsuario alcance, Pageable paginacion) {
        Page<Orden> pagina = ordenes.findAll(EspecificacionesOrden.con(filtro, alcance), paginacion);
        // Con @BatchSize, estas inicializaciones cargan las colecciones de toda
        // la pagina en pocas consultas.
        pagina.forEach(o -> {
            Hibernate.initialize(o.getProgramaciones());
            Hibernate.initialize(o.getDetalles());
        });
        return pagina;
    }

    /**
     * RF33: informacion general y cada detalle con su servidor, estado,
     * posicion y fechas previstas y reales, mas el historial de programacion y
     * de estados. Fuera de alcance responde "no encontrado".
     */
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
}
