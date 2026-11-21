package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.ScriptParametro;
import com.komainos.inventario.repository.ConteoPorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ScriptParametroRepositorio extends JpaRepository<ScriptParametro, Integer> {
    @Query("""
            select new com.komainos.inventario.repository.ConteoPorId(p.listaReferencia.id, count(p))
            from ScriptParametro p where p.listaReferencia is not null group by p.listaReferencia.id
            """)
    List<ConteoPorId> contarPorLista();

    long countByListaReferenciaId(Integer idLista);
}
