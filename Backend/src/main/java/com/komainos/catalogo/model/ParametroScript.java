package com.komainos.catalogo.model;

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
 * Parámetro declarado por una versión (RF25). En la ejecución se define como variable del mismo nombre con el valor por
 * defecto: un parámetro obligatorio necesita valor porque la MOP se ejecuta sin intervención (RF52).
 */
@Entity
@Table(name = "script_parametro")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParametroScript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_script_parametro")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_script_version", nullable = false)
    private VersionScript version;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_dato", nullable = false, columnDefinition = "enum_tipo_dato_parametro")
    private TipoDato tipoDato;

    @Column(name = "obligatorio", nullable = false)
    private boolean obligatorio;

    @Column(name = "valor_por_defecto", length = 1000)
    private String valorPorDefecto;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    public static ParametroScript nuevo(VersionScript version, String nombre, TipoDato tipo, boolean obligatorio,
                                        String valorPorDefecto, int orden) {
        ParametroScript parametro = new ParametroScript();
        parametro.version = version;
        parametro.nombre = nombre;
        parametro.tipoDato = tipo;
        parametro.obligatorio = obligatorio;
        parametro.valorPorDefecto = valorPorDefecto;
        parametro.orden = orden;
        return parametro;
    }
}
