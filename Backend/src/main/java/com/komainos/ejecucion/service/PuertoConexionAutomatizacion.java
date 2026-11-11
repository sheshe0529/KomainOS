package com.komainos.ejecucion.service;

import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;

/**
 * Resuelve la cuenta de servicio en el momento de conectarse. Lo implementa credencial, que es el único componente que
 * descifra secretos (DEC-38); solo lo usa el adaptador real y la conexión nunca se serializa ni se registra (RNF11).
 */
public interface PuertoConexionAutomatizacion {

    Conexion resolver(Integer idCuenta, FamiliaSistemaOperativo familia, PropositoEjecucion proposito);

    /** Datos de acceso en claro: solo viven en memoria durante la ejecución */
    final class Conexion {

        private final String usuario;
        private final String secreto;
        private final boolean llave;
        private final MetodoElevacion metodoElevacion;
        private final String secretoElevacion;

        /**
         * @param llave            el secreto es una llave privada SSH y no una contraseña
         * @param metodoElevacion  cómo obtiene privilegios Ansible en esta ejecución (DEC-57)
         * @param secretoElevacion la contraseña que pide sudo o la de root para su; nula sin contraseña de elevación
         */
        public Conexion(String usuario, String secreto, boolean llave, MetodoElevacion metodoElevacion,
                        String secretoElevacion) {
            this.usuario = usuario;
            this.secreto = secreto;
            this.llave = llave;
            this.metodoElevacion = metodoElevacion == null ? MetodoElevacion.NINGUNO : metodoElevacion;
            this.secretoElevacion = secretoElevacion;
        }

        /** Sin elevación */
        public Conexion(String usuario, String secreto, boolean llave) {
            this(usuario, secreto, llave, MetodoElevacion.NINGUNO, null);
        }

        public String usuario() {
            return usuario;
        }

        public String secreto() {
            return secreto;
        }

        public boolean llave() {
            return llave;
        }

        public MetodoElevacion metodoElevacion() {
            return metodoElevacion;
        }

        public String secretoElevacion() {
            return secretoElevacion;
        }

        @Override
        public String toString() {
            return "Conexion[protegida, elevacion=%s]".formatted(metodoElevacion);
        }
    }
}
