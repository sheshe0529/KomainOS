package com.komainos.shared.util.archivo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Todo como texto: una VLAN o una versión como 22.04 no deben reinterpretarse (RF13) */
@Component
public class EscritorTabular {

    /** En caracteres */
    private static final int ANCHO_MAXIMO = 60;
    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final ObjectMapper json;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
    private final CsvMapper csv = new CsvMapper();

    public EscritorTabular(ObjectMapper json) {
        this.json = json;
    }

    public byte[] escribir(FormatoArchivo formato, List<ColumnaArchivo> columnas,
                           List<Map<String, ?>> filas, String titulo) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            escribir(formato, columnas, filas.iterator(), titulo, salida);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el archivo " + formato, ex);
        }
        return salida.toByteArray();
    }

    /**
     * Escribe cada fila a medida que la recibe, sin acumular las filas ni el archivo: así una exportación puede ir
     * directo a la respuesta. No cierra la salida, que es de quien la abrió.
     */
    public void escribir(FormatoArchivo formato, List<ColumnaArchivo> columnas,
                         Iterator<? extends Map<String, ?>> filas, String titulo, OutputStream salida)
            throws IOException {
        switch (formato) {
            case XLSX -> xlsx(columnas, filas, titulo, salida);
            case CSV -> csv(columnas, filas, salida);
            case JSON -> secuencia(json.writerWithDefaultPrettyPrinter(), columnas, filas, salida);
            case YAML -> secuencia(yaml.writer(), columnas, filas, salida);
        }
    }

    /** Inyección de fórmulas (OWASP): en el CSV se antepone un apóstrofo y al importarlo se retira */
    static boolean iniciaComoFormula(String valor) {
        return !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0;
    }

    /** También un apóstrofo inicial: si no se escapara, al importar se confundiría con el escape y se perdería */
    static boolean requiereEscape(String valor) {
        return iniciaComoFormula(valor) || (!valor.isEmpty() && valor.charAt(0) == '\'');
    }

    /** Reversible con LectorTabular.desescapar: el valor vuelve exactamente igual (una contraseña puede empezar así) */
    static String escaparCsv(String valor) {
        return valor != null && requiereEscape(valor) ? "'" + valor : valor;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static Map<String, String> objeto(List<ColumnaArchivo> columnas, Map<String, ?> fila) {
        Map<String, String> objeto = new LinkedHashMap<>();
        columnas.forEach(c -> objeto.put(c.clave(), texto(fila.get(c.clave()))));
        return objeto;
    }

    /** JSON y YAML: la misma lista de objetos que se escribiría de una vez, pero elemento por elemento */
    private static void secuencia(ObjectWriter escritor, List<ColumnaArchivo> columnas,
                                  Iterator<? extends Map<String, ?>> filas, OutputStream salida) throws IOException {
        try (SequenceWriter lista = escritor.without(JsonGenerator.Feature.AUTO_CLOSE_TARGET).writeValuesAsArray(salida)) {
            while (filas.hasNext()) {
                lista.write(objeto(columnas, filas.next()));
            }
        }
    }

    private void csv(List<ColumnaArchivo> columnas, Iterator<? extends Map<String, ?>> filas, OutputStream salida)
            throws IOException {
        CsvSchema.Builder esquema = CsvSchema.builder().setUseHeader(true);
        columnas.forEach(c -> esquema.addColumn(c.etiqueta()));
        // El BOM hace que Excel reconozca UTF-8 y muestre bien las tildes
        salida.write(BOM_UTF8);
        try (SequenceWriter registros = csv.writer(esquema.build())
                .without(JsonGenerator.Feature.AUTO_CLOSE_TARGET).writeValues(salida)) {
            while (filas.hasNext()) {
                Map<String, ?> fila = filas.next();
                List<String> celdas = new ArrayList<>(columnas.size());
                for (ColumnaArchivo c : columnas) {
                    celdas.add(escaparCsv(texto(fila.get(c.clave()))));
                }
                registros.write(celdas);
            }
        }
    }

    private static void xlsx(List<ColumnaArchivo> columnas, Iterator<? extends Map<String, ?>> filas, String titulo,
                             OutputStream salida) throws IOException {
        // SXSSF escribe por bloques: la memoria no crece con el inventario
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
            while (filas.hasNext()) {
                Map<String, ?> fila = filas.next();
                Row registro = hoja.createRow(numero++);
                for (int c = 0; c < columnas.size(); c++) {
                    String valor = texto(fila.get(columnas.get(c).clave()));
                    if (valor != null) {
                        // Como texto: nunca se interpreta como fórmula
                        registro.createCell(c).setCellValue(valor);
                        anchos[c] = Math.max(anchos[c], valor.length());
                    }
                }
            }
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, (Math.min(anchos[c], ANCHO_MAXIMO) + 2) * 256);
            }
            hoja.createFreezePane(0, 1);

            libro.write(salida);
            libro.dispose();
        }
    }
}
