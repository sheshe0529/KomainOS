package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.EstadoServidor;
import com.komainos.inventario.dominio.FamiliaSistemaOperativo;
import com.komainos.shared.api.ReferenciaSimple;

import java.time.Instant;

/**
 * Fila del inventario (RF11). Todas las columnas son visibles para todos los
 * roles en esta iteracion porque ninguna contiene secretos (DEC-17).
 */
public record ServidorResumenRespuesta(
        Integer id,
        String hostname,
        String direccionIp,
        String datacenter,
        String servidorFisico,
        /** Sistema operativo (id y nombre); los grupos lo comparan (RF20). */
        ReferenciaSimple sistemaOperativo,
        /** Version con su nombre completo, por ejemplo "Ubuntu 22.04". */
        ReferenciaSimple versionSistemaOperativo,
        FamiliaSistemaOperativo familiaSistemaOperativo,
        ReferenciaSimple entorno,
        CriticidadResumen criticidad,
        ReferenciaSimple responsable,
        EstadoServidor estado,
        Instant fechaActualizacion) {

    /** Nivel de criticidad con su prioridad, para ordenar y colorear en el panel. */
    public record CriticidadResumen(Integer id, String nombre, Integer prioridad) {
    }
}
