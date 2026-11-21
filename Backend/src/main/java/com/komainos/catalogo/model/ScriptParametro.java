package com.komainos.catalogo.model;

import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.TipoDato;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/**
 * Parámetro declarado por una versión (RF25). En la ejecución se define como variable del mismo nombre:
 * un escalar con valor o los elementos vigentes de una lista del catálogo (RF52).
 */
@Entity
@Table(name = "script_parametro")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScriptParametro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_script_parametro")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_script_version", nullable = false)
    private ScriptVersion version;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    /** Tipo del escalar; nulo en un parámetro de lista, cuyos elementos tienen el tipo de su lista (DEC-60) */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_dato", columnDefinition = "enum_tipo_dato_parametro")
    private TipoDato tipoDato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lista_referencia")
    private ListaReferencia listaReferencia;

    @Column(name = "valor_por_defecto", length = 1000)
    private String valorPorDefecto;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    public static ScriptParametro nuevo(ScriptVersion version, String nombre, TipoParametro tipo,
                                        String valorPorDefecto, ListaReferencia lista, int orden) {
        ScriptParametro parametro = new ScriptParametro();
        parametro.version = version;
        parametro.nombre = nombre;
        parametro.tipoDato = tipo == TipoParametro.LISTA ? null : tipo.tipoEscalar();
        parametro.listaReferencia = lista;
        parametro.valorPorDefecto = valorPorDefecto;
        parametro.orden = orden;
        return parametro;
    }

    /** Forma del parámetro: LISTA si toma los elementos de una lista de referencia, si no su tipo escalar */
    public TipoParametro getTipo() {
        return tipoDato == null ? TipoParametro.LISTA : TipoParametro.valueOf(tipoDato.name());
    }
}
