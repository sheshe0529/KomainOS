package com.komainos.shared.archivos;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza encabezados para reconocer una columna escrita de distintas formas:
 * «Dirección IP», «direccion_ip», «DIRECCION IP» y «direccionIp» son la misma
 * clave {@code direccion_ip}. Asi un archivo exportado (con etiquetas en XLSX
 * y CSV, y claves en JSON y YAML) se puede volver a importar sin editarlo.
 */
public final class ClaveColumna {

    private ClaveColumna() {
    }

    public static String normalizar(String encabezado) {
        if (encabezado == null) {
            return "";
        }
        String texto = encabezado.replace("\uFEFF", "").trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        texto = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return texto.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
