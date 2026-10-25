package com.komainos.inventario.service;

import com.komainos.seguridad.model.Usuario;

import java.time.Instant;

/** Implementado en mantenimiento para que la dependencia vaya de mantenimiento hacia inventario (DEC-27) */
public interface PuertoMantenimientos {

    /** Orden cuyo ciclo ya empezó y no terminó: la baja queda pendiente (DEC-25) */
    boolean tieneMantenimientoEnCurso(Integer idServidor);

    /** Órdenes que todavía van a ejecutarse con esa cuenta de servicio: la guardaron al generarse (R2.4) */
    long ordenesPendientesConCuenta(Integer idCuentaServicio);

    /** Cancela las órdenes individuales y lo retira de las grupales sin afectar a los demás integrantes */
    int retirarPendientes(Integer idServidor, Usuario autor, String motivo, Instant ahora);
}
