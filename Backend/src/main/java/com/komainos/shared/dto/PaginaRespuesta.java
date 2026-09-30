package com.komainos.shared.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltura de paginacion propia.
 *
 * <p>Se usa en lugar de serializar {@link Page} directamente porque el JSON de
 * Spring Data expone estructura interna (pageable, sort, first/last) que no es
 * estable entre versiones. Al fijar la forma aqui, el cliente de API del panel
 * puede tipar una sola vez la respuesta de cualquier listado.
 */
public record PaginaRespuesta<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaRespuesta<T> de(Page<T> pagina) {
        return new PaginaRespuesta<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }

    public static <E, T> PaginaRespuesta<T> de(Page<E> pagina, Function<E, T> mapeador) {
        return new PaginaRespuesta<>(
                pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
