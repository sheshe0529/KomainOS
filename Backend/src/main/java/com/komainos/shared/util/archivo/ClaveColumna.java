package com.komainos.shared.util.archivo;

import java.text.Normalizer;
import java.util.Locale;

/** «Dirección IP», «direccion_ip» y «direccionIp» son la misma clave: un archivo exportado se reimporta sin editarlo */
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
