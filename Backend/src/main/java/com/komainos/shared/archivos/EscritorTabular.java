package com.komainos.shared.archivos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Escribe tablas en los formatos de intercambio (RF13). Todos los valores se
 * escriben como texto: el archivo sirve para trabajar fuera del sistema y para
 * volver a importarlo, y un número de VLAN o una versión como {@code 22.04}
 * no deben reinterpretarse.
 *
 * <p>XLSX y CSV llevan como encabezado la etiqueta legible de cada columna;
 * JSON y YAML, su clave. Las dos formas se reconocen al importar.
 */
@Component
public class EscritorTabular {

    /** Ancho maximo de columna en XLSX, en caracteres. */
    private static final int ANCHO_MAXIMO = 60;

    private final ObjectMapper json;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    private final CsvMapper csv = new CsvMapper();

    public EscritorTabular(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @param filas valores por clave de columna; {@code null} deja la celda vacia
     */
    public byte[] escribir(FormatoArchivo formato, List<ColumnaArchivo> columnas,
                           List<Map<String, ?>> filas, String titulo) {
        try {
            return switch (formato) {
                case XLSX -> xlsx(columnas, filas, titulo);
                case CSV -> csv(columnas, filas);
                case JSON -> json.writerWithDefaultPrettyPrinter().writeValueAsBytes(objetos(columnas, filas));
                case YAML -> yaml.writeValueAsBytes(objetos(columnas, filas));
            };
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el archivo " + formato, ex);
        }
    }

    /**
     * Excel y otras hojas de calculo ejecutan como formula una celda de CSV que
     * empieza por estos caracteres (inyeccion de formulas, OWASP). Al exportar
     * se antepone un apostrofo; al importar se retira.
     */
    static boolean iniciaComoFormula(String valor) {
        return !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static List<Map<String, String>> objetos(List<ColumnaArchivo> columnas, List<Map<String, ?>> filas) {
        List<Map<String, String>> objetos = new ArrayList<>(filas.size());
        for (Map<String, ?> fila : filas) {
            Map<String, String> objeto = new LinkedHashMap<>();
            columnas.forEach(c -> objeto.put(c.clave(), texto(fila.get(c.clave()))));
            objetos.add(objeto);
        }
        return objetos;
    }

    private byte[] csv(List<ColumnaArchivo> columnas, List<Map<String, ?>> filas) throws JsonProcessingException {
        CsvSchema.Builder esquema = CsvSchema.builder().setUseHeader(true);
        columnas.forEach(c -> esquema.addColumn(c.etiqueta()));
        List<List<String>> registros = new ArrayList<>(filas.size());
        for (Map<String, ?> fila : filas) {
            List<String> celdas = new ArrayList<>(columnas.size());
            for (ColumnaArchivo c : columnas) {
                String valor = texto(fila.get(c.clave()));
                celdas.add(valor != null && iniciaComoFormula(valor) ? "'" + valor : valor);
            }
            registros.add(celdas);
        }
        String contenido = csv.writer(esquema.build()).writeValueAsString(registros);
        // El BOM hace que Excel reconozca UTF-8 y muestre bien las tildes.
        return ("\uFEFF" + contenido).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xlsx(List<ColumnaArchivo> columnas, List<Map<String, ?>> filas, String titulo)
            throws IOException {
        // SXSSF escribe por bloques: el consumo de memoria no crece con el inventario.
        try (SXSSFWorkbook libro = new SXSSFWorkbook(200)) {
            Sheet hoja = libro.createSheet(titulo);
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);

            int[] anchos = new int[columnas.size()];
            Row encabezado = hoja.createRow(0);
            for (int c = 0; c < columnas.size(); c++) {
                var celda = encabezado.createCell(c);
                celda.setCellValue(columnas.get(c).etiqueta());
                celda.setCellStyle(estiloEncabezado);
                anchos[c] = columnas.get(c).etiqueta().length();
            }
            int numero = 1;
            for (Map<String, ?> fila : filas) {
                Row registro = hoja.createRow(numero++);
                for (int c = 0; c < columnas.size(); c++) {
                    String valor = texto(fila.get(columnas.get(c).clave()));
                    if (valor != null) {
                        // Como texto: nunca se interpreta como formula.
                        registro.createCell(c).setCellValue(valor);
                        anchos[c] = Math.max(anchos[c], valor.length());
                    }
                }
            }
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, (Math.min(anchos[c], ANCHO_MAXIMO) + 2) * 256);
            }
            hoja.createFreezePane(0, 1);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            libro.dispose();
            return salida.toByteArray();
        }
    }
}
