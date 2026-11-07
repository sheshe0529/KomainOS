package com.komainos.catalogo.service;

import com.komainos.catalogo.repository.ParametroScriptRepositorio;
import com.komainos.inventario.repository.ConteoPorId;
import com.komainos.inventario.service.PuertoParametrosLista;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/** Incluye versiones históricas: una referencia no deja de usarse por deshabilitar la versión. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdaptadorParametrosLista implements PuertoParametrosLista {
    private final ParametroScriptRepositorio parametros;

    @Override
    public Map<Integer, Long> contarPorLista() {
        return parametros.contarPorLista().stream()
                .collect(Collectors.toMap(ConteoPorId::id, ConteoPorId::cantidad));
    }

    @Override
    public long contarDeLista(Integer idLista) {
        return parametros.countByListaReferenciaId(idLista);
    }
}
