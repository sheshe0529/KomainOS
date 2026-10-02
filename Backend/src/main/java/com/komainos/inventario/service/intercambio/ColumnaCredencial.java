package com.komainos.inventario.service.intercambio;

import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.shared.util.archivo.ClaveColumna;
import com.komainos.shared.util.archivo.ColumnaArchivo;
import com.komainos.shared.util.archivo.FilaArchivo;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Columnas de la credencial principal al final del inventario, solo para el administrador (RF12, RF13, DEC-39) */
public enum ColumnaCredencial {

    MECANISMO("Mecanismo de acceso"),
    TIPO_USUARIO("Tipo de usuario"),
    USUARIO("Usuario de acceso"),
    SECRETO("Contraseña o llave"),
    SECRETO_SU("Contraseña su (root)");

    /** Nombre del cambio en la vista previa de la importación */
    public static final String CAMBIO = "Credencial principal";

    private final String etiqueta;
    private final String clave;

    ColumnaCredencial(String etiqueta) {
        this.etiqueta = etiqueta;
        this.clave = ClaveColumna.normalizar(etiqueta);
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String clave() {
        return clave;
    }

    public ColumnaArchivo comoColumnaArchivo() {
        return new ColumnaArchivo(clave, etiqueta);
    }

    public static ColumnaCredencial deClave(String texto) {
        String buscada = ClaveColumna.normalizar(texto);
        return Arrays.stream(values()).filter(c -> c.clave.equals(buscada)).findFirst().orElse(null);
    }

    public static boolean algunaEn(Collection<String> claves) {
        return claves.stream().anyMatch(c -> deClave(c) != null);
    }

    /** Nula si todas las celdas están vacías, los secretos se leen sin recortar */
    public static CredencialArchivo leer(FilaArchivo fila) {
        CredencialArchivo credencial = new CredencialArchivo(fila.valor(MECANISMO.clave), fila.valor(TIPO_USUARIO.clave),
                fila.valor(USUARIO.clave), fila.literal(SECRETO.clave), fila.literal(SECRETO_SU.clave));
        boolean vacia = credencial.mecanismo() == null && credencial.tipoUsuario() == null && credencial.usuario() == null
                && credencial.secreto() == null && credencial.secretoSu() == null;
        return vacia ? null : credencial;
    }

    public static Map<String, Object> valores(CredencialArchivo c) {
        Map<String, Object> valores = new HashMap<>();
        if (c != null) {
            valores.put(MECANISMO.clave, c.mecanismo());
            valores.put(TIPO_USUARIO.clave, c.tipoUsuario());
            valores.put(USUARIO.clave, c.usuario());
            valores.put(SECRETO.clave, c.secreto());
            valores.put(SECRETO_SU.clave, c.secretoSu());
        }
        return valores;
    }
}
