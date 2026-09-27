package com.komainos.inventario.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Setter;

/**
 * Version de un sistema operativo (tabla {@code version_sistema_operativo}).
 * La combinacion sistema operativo + version es unica.
 */
@Entity
@Table(name = "version_sistema_operativo")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersionSistemaOperativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_version_sistema_operativo")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sistema_operativo", nullable = false)
    private SistemaOperativo sistemaOperativo;

    @Column(name = "version", nullable = false, length = 150)
    private String version;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    public static VersionSistemaOperativo nueva(SistemaOperativo so, String version) {
        VersionSistemaOperativo v = new VersionSistemaOperativo();
        v.sistemaOperativo = so;
        v.version = version;
        v.activo = true;
        return v;
    }

    /** Texto para mostrar: "Ubuntu 22.04". */
    public String descripcionCompleta() {
        return sistemaOperativo.getNombre() + " " + version;
    }
}
