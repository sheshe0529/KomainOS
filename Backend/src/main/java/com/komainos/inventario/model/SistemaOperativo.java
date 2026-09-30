package com.komainos.inventario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogo de sistemas operativos (tabla {@code sistema_operativo}). No tiene
 * requisito propio: es una dependencia de RF10, porque todo servidor referencia
 * una version de sistema operativo, y de RF20 (grupos del mismo SO).
 */
@Entity
@Table(name = "sistema_operativo")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SistemaOperativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sistema_operativo")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "familia", nullable = false, columnDefinition = "enum_familia_so")
    private FamiliaSistemaOperativo familia;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @OneToMany(mappedBy = "sistemaOperativo")
    @OrderBy("version ASC")
    private List<VersionSistemaOperativo> versiones = new ArrayList<>();

    public static SistemaOperativo nuevo(String nombre, FamiliaSistemaOperativo familia) {
        SistemaOperativo so = new SistemaOperativo();
        so.nombre = nombre;
        so.familia = familia;
        so.activo = true;
        return so;
    }
}
