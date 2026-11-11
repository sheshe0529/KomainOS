package com.komainos.ejecucion.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;

/**
 * Cómo obtiene privilegios Ansible después de conectarse con la cuenta (DEC-57). Se aplica tal cual en todas las
 * ejecuciones de la cuenta, análisis incluido: quién se eleva y quién no lo decide la configuración de la cuenta.
 * <ul>
 *   <li>Linux: ninguno, sudo (con la contraseña del propio usuario o sin contraseña) o su con la contraseña de root.</li>
 *   <li>Windows: ninguno o runas con la misma cuenta, que le da el token completo de administrador aunque la conexión
 *   remota lo reciba filtrado por el control de cuentas de usuario.</li>
 * </ul>
 */
public enum MetodoElevacion {
    NINGUNO("Ninguno"),
    SUDO("sudo"),
    SU("su"),
    RUNAS("runas");

    private final String etiqueta;

    MetodoElevacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Como se escribe en los archivos de intercambio y se muestra en el panel */
    public String etiqueta() {
        return etiqueta;
    }

    /** sudo y su existen solo en Linux y runas solo en Windows; familia nula admite cualquiera */
    public boolean admitidoEn(FamiliaSistemaOperativo familia) {
        return switch (this) {
            case NINGUNO -> true;
            case SUDO, SU -> familia != FamiliaSistemaOperativo.WINDOWS;
            case RUNAS -> familia != FamiliaSistemaOperativo.LINUX;
        };
    }

    /** sudo puede pedir o no una contraseña; su siempre pide la de root; runas usa la contraseña de la propia cuenta */
    public boolean admiteContrasena() {
        return this == SUDO || this == SU;
    }

    public boolean exigeContrasena() {
        return this == SU;
    }

    /** Valor de ansible_become_method; nulo si no hay elevación */
    public String metodoAnsible() {
        return switch (this) {
            case NINGUNO -> null;
            case SUDO -> "sudo";
            case SU -> "su";
            case RUNAS -> "runas";
        };
    }

    /** La familia en la que tiene sentido, para los mensajes; nula si sirve en ambas */
    public FamiliaSistemaOperativo familia() {
        return switch (this) {
            case NINGUNO -> null;
            case SUDO, SU -> FamiliaSistemaOperativo.LINUX;
            case RUNAS -> FamiliaSistemaOperativo.WINDOWS;
        };
    }
}
