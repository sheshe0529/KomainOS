package com.komainos.inventario.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/**
 * Configuracion de mantenimiento de un grupo (RF17), tabla
 * {@code configuracion_grupo}. Agrega el modo de ejecucion de los integrantes.
 * Los mantenimientos grupales la usan sin reemplazar la configuracion
 * individual de cada miembro (RF46).
 */
@Entity
@Table(name = "configuracion_grupo")
@PrimaryKeyJoinColumn(name = "id_configuracion_mantenimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfiguracionGrupo extends ConfiguracionMantenimiento {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_mantenimiento", nullable = false, unique = true)
    private GrupoMantenimiento grupo;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "modo_ejecucion", nullable = false, columnDefinition = "enum_modo_ejecucion")
    private ModoEjecucion modoEjecucion;

    public static ConfiguracionGrupo nueva(GrupoMantenimiento grupo, int frecuenciaRevisionDias,
                                           int frecuenciaMantenimientoDias, ModalidadPlanificacion modalidad,
                                           ModoEjecucion modoEjecucion) {
        ConfiguracionGrupo configuracion = new ConfiguracionGrupo();
        configuracion.grupo = grupo;
        configuracion.actualizar(frecuenciaRevisionDias, frecuenciaMantenimientoDias, modalidad, modoEjecucion);
        return configuracion;
    }

    public void actualizar(int frecuenciaRevisionDias, int frecuenciaMantenimientoDias,
                           ModalidadPlanificacion modalidad, ModoEjecucion modoEjecucion) {
        aplicarComunes(frecuenciaRevisionDias, frecuenciaMantenimientoDias, modalidad);
        this.modoEjecucion = modoEjecucion;
    }
}
