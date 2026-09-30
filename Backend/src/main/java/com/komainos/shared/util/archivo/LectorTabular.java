package com.komainos.shared.util.archivo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lee archivos de intercambio (RF12) a una tabla de texto con claves
 * normalizadas. No interpreta el contenido: validar cada valor le corresponde
 * al caso de uso que lo importa.
 *
 * <ul>
 *   <li><b>XLSX</b>: primera hoja; la primera fila con datos es el encabezado.
 *       Las formulas se leen por su ultimo valor calculado, sin evaluarlas.</li>
 *   <li><b>CSV</b>: UTF-8 (con o sin BOM) o, si no lo es, Windows-1252, que es
 *       como lo guarda Excel en espanol; separador coma, punto y coma o
 *       tabulador, detectado en el encabezado.</li>
 *   <li><b>JSON / YAML</b>: una lista de objetos, o un objeto con una unica
 *       propiedad que sea esa lista (por ejemplo {@code servidores}).</li>
 * </ul>
 */
@Slf4j
@Component
public class LectorTabular {

    /** Tope de registros por archivo: el inventario se carga por lotes razonables. */
    public static final int MAXIMO_REGISTROS = 5000;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final ObjectMapper json;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final CsvMapper csv = new CsvMapper();

    public LectorTabular(ObjectMapper json) {
        this.json = json;
    }

    public TablaArchivo leer(FormatoArchivo formato, byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new ReglaNegocioException("El archivo está vacío");
        }
        try {
            return switch (formato) {
                case XLSX -> desdeCeldas(leerHoja(contenido));
                case CSV -> desdeCeldas(leerCsv(contenido));
                case JSON -> desdeArbol(json.readTree(contenido));
                case YAML -> desdeArbol(yaml.readTree(contenido));
            };
        } catch (ReglaNegocioException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // El detalle tecnico queda en el log; al usuario le basta saber que
            // el contenido no corresponde al formato indicado.
            log.info("Archivo {} ilegible: {}", formato, ex.toString());
            throw new ReglaNegocioException("No se pudo leer el archivo como %s. Verifique que el contenido corresponda al formato"
                    .formatted(formato.name()));
        }
    }

    // ------------------------------------------------------------ XLSX y CSV

    private record FilaCeldas(int numero, List<String> celdas) {
    }

    private static List<FilaCeldas> leerHoja(byte[] contenido) throws IOException {
        List<FilaCeldas> filas = new ArrayList<>();
        // POI rechaza por si mismo los archivos comprimidos con una tasa de
        // compresion anomala (ZipSecureFile), el caso tipico de un XLSX malicioso.
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            if (libro.getNumberOfSheets() == 0) {
                return filas;
            }
            Sheet hoja = libro.getSheetAt(0);
            DataFormatter formateador = new DataFormatter(Locale.ROOT);
            formateador.setUseCachedValuesForFormulaCells(true);
            for (Row fila : hoja) {
                List<String> celdas = new ArrayList<>();
                for (int c = 0; c < Math.max(fila.getLastCellNum(), 0); c++) {
                    celdas.add(formateador.formatCellValue(fila.getCell(c)));
                }
                filas.add(new FilaCeldas(fila.getRowNum() + 1, celdas));
                exigirTope(filas.size() - 1);
            }
        }
        return filas;
    }

    private List<FilaCeldas> leerCsv(byte[] contenido) throws IOException {
        String texto = decodificar(contenido);
        CsvSchema esquema = CsvSchema.emptySchema().withColumnSeparator(separadorDe(texto));
        List<FilaCeldas> filas = new ArrayList<>();
        try (MappingIterator<List<String>> registros = csv.readerForListOf(String.class)
                .with(CsvParser.Feature.WRAP_AS_ARRAY)
                .with(esquema)
                .readValues(texto)) {
            int numero = 0;
            while (registros.hasNextValue()) {
                filas.add(new FilaCeldas(++numero, registros.nextValue()));
                exigirTope(filas.size() - 1);
            }
        }
        return filas;
    }

    /** UTF-8 estricto; si no lo es, el archivo viene de Excel con la codificacion regional. */
    private static String decodificar(byte[] contenido) {
        String texto;
        try {
            texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido))
                    .toString();
        } catch (CharacterCodingException ex) {
            texto = new String(contenido, WINDOWS_1252);
        }
        return texto.startsWith("\uFEFF") ? texto.substring(1) : texto;
    }

    /** El separador mas frecuente en la primera linea; coma si no hay ninguno. */
    private static char separadorDe(String texto) {
        int fin = texto.indexOf('\n');
        String encabezado = fin < 0 ? texto : texto.substring(0, fin);
        char elegido = ',';
        long maximo = encabezado.chars().filter(c -> c == ',').count();
        for (char candidato : new char[]{';', '\t'}) {
            long veces = encabezado.chars().filter(c -> c == candidato).count();
            if (veces > maximo) {
                maximo = veces;
                elegido = candidato;
            }
        }
        return elegido;
    }

    private static TablaArchivo desdeCeldas(List<FilaCeldas> filas) {
        Iterator<FilaCeldas> it = filas.stream().filter(f -> !estaVacia(f.celdas())).iterator();
        if (!it.hasNext()) {
            throw new ReglaNegocioException("El archivo no contiene registros");
        }
        List<String> encabezado = it.next().celdas().stream().map(ClaveColumna::normalizar).toList();
        exigirEncabezadosUnicos(encabezado);

        List<FilaArchivo> registros = new ArrayList<>();
        while (it.hasNext()) {
            FilaCeldas fila = it.next();
            Map<String, String> valores = new HashMap<>();
            for (int c = 0; c < Math.min(encabezado.size(), fila.celdas().size()); c++) {
                String valor = limpiar(fila.celdas().get(c));
                if (!encabezado.get(c).isEmpty() && valor != null) {
                    valores.put(encabezado.get(c), valor);
                }
            }
            registros.add(new FilaArchivo(fila.numero(), valores, List.of()));
        }
        if (registros.isEmpty()) {
            throw new ReglaNegocioException("El archivo solo contiene el encabezado, sin registros");
        }
        return new TablaArchivo(encabezado.stream().filter(c -> !c.isEmpty()).toList(), registros);
    }

    private static void exigirEncabezadosUnicos(List<String> encabezado) {
        Set<String> vistos = new LinkedHashSet<>();
        for (String clave : encabezado) {
            if (!clave.isEmpty() && !vistos.add(clave)) {
                throw new ReglaNegocioException("La columna «%s» aparece más de una vez en el encabezado".formatted(clave));
            }
        }
    }

    private static boolean estaVacia(List<String> celdas) {
        return celdas.stream().allMatch(c -> c == null || c.isBlank());
    }

    // ------------------------------------------------------------ JSON y YAML

    private static TablaArchivo desdeArbol(JsonNode raiz) {
        JsonNode lista = raiz;
        if (raiz != null && raiz.isObject() && raiz.size() == 1 && raiz.elements().next().isArray()) {
            lista = raiz.elements().next();
        }
        if (lista == null || !lista.isArray()) {
            throw new ReglaNegocioException("El archivo debe contener una lista de registros");
        }
        if (lista.isEmpty()) {
            throw new ReglaNegocioException("El archivo no contiene registros");
        }
        exigirTope(lista.size());

        Set<String> columnas = new LinkedHashSet<>();
        List<FilaArchivo> registros = new ArrayList<>();
        int numero = 0;
        for (JsonNode nodo : lista) {
            numero++;
            if (!nodo.isObject()) {
                registros.add(new FilaArchivo(numero, Map.of(), List.of("El registro no es un objeto con campos")));
                continue;
            }
            Map<String, String> valores = new LinkedHashMap<>();
            List<String> problemas = new ArrayList<>();
            var campos = nodo.fields();
            while (campos.hasNext()) {
                var campo = campos.next();
                String clave = ClaveColumna.normalizar(campo.getKey());
                if (clave.isEmpty()) {
                    continue;
                }
                columnas.add(clave);
                JsonNode valor = campo.getValue();
                if (valor.isContainerNode()) {
                    problemas.add("El campo «%s» debe tener un valor simple".formatted(campo.getKey()));
                } else if (!valor.isNull()) {
                    String texto = limpiar(valor.asText());
                    if (texto != null) {
                        valores.put(clave, texto);
                    }
                }
            }
            registros.add(new FilaArchivo(numero, valores, List.copyOf(problemas)));
        }
        return new TablaArchivo(List.copyOf(columnas), registros);
    }

    // ------------------------------------------------------------- utilidades

    /**
     * Recorta y descarta el apostrofo con que se neutraliza una formula al
     * exportar (ver {@link EscritorTabular}), para que el archivo exportado se
     * pueda volver a importar tal cual.
     */
    static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.length() > 1 && texto.charAt(0) == '\'' && EscritorTabular.iniciaComoFormula(texto.substring(1))) {
            texto = texto.substring(1);
        }
        return texto.isEmpty() ? null : texto;
    }

    private static void exigirTope(int registros) {
        if (registros > MAXIMO_REGISTROS) {
            throw new ReglaNegocioException("El archivo supera el máximo de %d registros por importación"
                    .formatted(MAXIMO_REGISTROS));
        }
    }
}
