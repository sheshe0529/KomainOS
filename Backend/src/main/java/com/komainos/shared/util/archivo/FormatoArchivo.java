package com.komainos.shared.util.archivo;

import com.komainos.shared.exception.ReglaNegocioException;

import java.util.Locale;

/** Formatos de intercambio del inventario (RF12, RF13). */
public enum FormatoArchivo {

    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv;charset=UTF-8"),
    YAML("yaml", "application/yaml"),
    JSON("json", "application/json");

    private final String extension;
    private final String tipoContenido;

    FormatoArchivo(String extension, String tipoContenido) {
        this.extension = extension;
        this.tipoContenido = tipoContenido;
    }

    public String extension() {
        return extension;
    }

    public String tipoContenido() {
        return tipoContenido;
    }

    /**
     * Deduce el formato por la extension del archivo recibido. Se decide por
     * la extension y no por el tipo MIME que envia el navegador, que para CSV
     * y YAML varia segun el sistema operativo.
     */
    public static FormatoArchivo deNombreArchivo(String nombre) {
        String minusculas = nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
        int punto = minusculas.lastIndexOf('.');
        String extension = punto < 0 ? "" : minusculas.substring(punto + 1);
        return switch (extension) {
            case "xlsx" -> XLSX;
            case "csv" -> CSV;
            case "yaml", "yml" -> YAML;
            case "json" -> JSON;
            default -> throw new ReglaNegocioException(
                    "Formato de archivo no admitido. Use un archivo XLSX, CSV, YAML o JSON");
        };
    }
}
