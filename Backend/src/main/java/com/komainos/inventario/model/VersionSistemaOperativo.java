package com.komainos.inventario.model;

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

    public String descripcionCompleta() {
        return sistemaOperativo.getNombre() + " " + version;
    }
}
