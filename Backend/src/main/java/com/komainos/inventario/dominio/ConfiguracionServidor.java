package com.komainos.inventario.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Configuracion de mantenimiento de un servidor (RF17), tabla
 * {@code configuracion_servidor}. Se elimina al aplicarse la baja (HU13 CA9).
 */
@Entity
@Table(name = "configuracion_servidor")
@PrimaryKeyJoinColumn(name = "id_configuracion_mantenimiento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfiguracionServidor extends ConfiguracionMantenimiento {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servidor", nullable = false, unique = true)
    private Servidor servidor;

    public static ConfiguracionServidor nueva(Servidor servidor, int frecuenciaRevisionDias,
                                              int frecuenciaMantenimientoDias, ModalidadPlanificacion modalidad) {
        ConfiguracionServidor configuracion = new ConfiguracionServidor();
        configuracion.servidor = servidor;
        configuracion.aplicarComunes(frecuenciaRevisionDias, frecuenciaMantenimientoDias, modalidad);
        return configuracion;
    }

    public void actualizar(int frecuenciaRevisionDias, int frecuenciaMantenimientoDias,
                           ModalidadPlanificacion modalidad) {
        aplicarComunes(frecuenciaRevisionDias, frecuenciaMantenimientoDias, modalidad);
    }
}
