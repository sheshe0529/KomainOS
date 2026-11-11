package com.komainos.inventario.service.intercambio;

import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.shared.util.archivo.ClaveColumna;
import com.komainos.shared.util.archivo.ColumnaArchivo;
import com.komainos.shared.util.archivo.FilaArchivo;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Columnas de la credencial principal al final del inventario, solo para el administrador (RF12, RF13, DEC-39). Desde
 * el DEC-57 llevan el método de elevación y su contraseña; la importación sigue aceptando los encabezados anteriores
 * («Tipo de usuario» y «Contraseña su (root)») para no rechazar archivos exportados antes.
 */
public enum ColumnaCredencial {

    MECANISMO("Mecanismo de acceso", null),
    METODO_ELEVACION("Método de elevación", "Tipo de usuario"),
    USUARIO("Usuario de acceso", null),
    SECRETO("Contraseña o llave", null),
    SECRETO_ELEVACION("Contraseña de elevación", "Contraseña su (root)");

    /** Nombre del cambio en la vista previa de la importación */
    public static final String CAMBIO = "Credencial principal";

    private final String etiqueta;
    private final String clave;
    private final String claveAnterior;

    ColumnaCredencial(String etiqueta, String etiquetaAnterior) {
        this.etiqueta = etiqueta;
        this.clave = ClaveColumna.normalizar(etiqueta);
        this.claveAnterior = etiquetaAnterior == null ? null : ClaveColumna.normalizar(etiquetaAnterior);
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
        return Arrays.stream(values())
                .filter(c -> c.clave.equals(buscada) || buscada.equals(c.claveAnterior))
                .findFirst().orElse(null);
    }

    public static boolean algunaEn(Collection<String> claves) {
        return claves.stream().anyMatch(c -> deClave(c) != null);
    }

    /** Nula si todas las celdas están vacías, los secretos se leen sin recortar */
    public static CredencialArchivo leer(FilaArchivo fila) {
        CredencialArchivo credencial = new CredencialArchivo(MECANISMO.valorEn(fila, false),
                METODO_ELEVACION.valorEn(fila, false), USUARIO.valorEn(fila, false), SECRETO.valorEn(fila, true),
                SECRETO_ELEVACION.valorEn(fila, true));
        boolean vacia = credencial.mecanismo() == null && credencial.metodoElevacion() == null
                && credencial.usuario() == null && credencial.secreto() == null && credencial.secretoElevacion() == null;
        return vacia ? null : credencial;
    }

    public static Map<String, Object> valores(CredencialArchivo c) {
        Map<String, Object> valores = new HashMap<>();
        if (c != null) {
            valores.put(MECANISMO.clave, c.mecanismo());
            valores.put(METODO_ELEVACION.clave, c.metodoElevacion());
            valores.put(USUARIO.clave, c.usuario());
            valores.put(SECRETO.clave, c.secreto());
            valores.put(SECRETO_ELEVACION.clave, c.secretoElevacion());
        }
        return valores;
    }

    /** La celda con el encabezado actual o, si el archivo es anterior al DEC-57, con el encabezado de entonces */
    private String valorEn(FilaArchivo fila, boolean literal) {
        String valor = literal ? fila.literal(clave) : fila.valor(clave);
        if (valor == null && claveAnterior != null) {
            valor = literal ? fila.literal(claveAnterior) : fila.valor(claveAnterior);
        }
        return valor;
    }
}
