package com.komainos.ejecucion.service;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

/**
 * Resuelve la cuenta de servicio en el momento de conectarse. Lo implementa credencial, que es el único componente que
 * descifra secretos (DEC-38); solo lo usa el adaptador real y la conexión nunca se serializa ni se registra (RNF11).
 */
public interface PuertoConexionAutomatizacion {

    Conexion resolver(Integer idCuenta, FamiliaSistemaOperativo familia);

    /** Datos de acceso en claro: solo viven en memoria durante la ejecución */
    final class Conexion {

        private final String usuario;
        private final String secreto;
        private final String secretoSu;
        private final boolean llave;

        /**
         * @param secretoSu contraseña de su para escalar a root en Linux; nula si la cuenta ya tiene privilegios
         * @param llave     el secreto es una llave privada SSH y no una contraseña
         */
        public Conexion(String usuario, String secreto, String secretoSu, boolean llave) {
            this.usuario = usuario;
            this.secreto = secreto;
            this.secretoSu = secretoSu;
            this.llave = llave;
        }

        public String usuario() {
            return usuario;
        }

        public String secreto() {
            return secreto;
        }

        public String secretoSu() {
            return secretoSu;
        }

        public boolean llave() {
            return llave;
        }

        @Override
        public String toString() {
            return "Conexion[protegida]";
        }
    }
}
