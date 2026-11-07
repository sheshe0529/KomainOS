package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.ParametroScript;
import com.komainos.inventario.repository.ConteoPorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ParametroScriptRepositorio extends JpaRepository<ParametroScript, Integer> {
    @Query("""
            select new com.komainos.inventario.repository.ConteoPorId(p.listaReferencia.id, count(p))
            from ParametroScript p where p.listaReferencia is not null group by p.listaReferencia.id
            """)
    List<ConteoPorId> contarPorLista();

    long countByListaReferenciaId(Integer idLista);
}
