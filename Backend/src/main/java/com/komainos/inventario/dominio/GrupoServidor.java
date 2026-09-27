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

import java.time.Instant;

/** Pertenencia de un servidor a un grupo (tabla {@code grupo_servidor}). */
@Entity
@Table(name = "grupo_servidor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrupoServidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo_servidor")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_mantenimiento", nullable = false)
    private GrupoMantenimiento grupo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false)
    private Servidor servidor;

    @Column(name = "fecha_incorporacion", nullable = false)
    private Instant fechaIncorporacion;

    static GrupoServidor nuevo(GrupoMantenimiento grupo, Servidor servidor, Instant ahora) {
        GrupoServidor integrante = new GrupoServidor();
        integrante.grupo = grupo;
        integrante.servidor = servidor;
        integrante.fechaIncorporacion = ahora;
        return integrante;
    }
}
