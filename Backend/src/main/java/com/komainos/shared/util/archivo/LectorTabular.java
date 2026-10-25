package com.komainos.shared.util.archivo;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/** CSV en UTF-8 o Windows-1252 (Excel en español), con el separador detectado en el encabezado */
@Slf4j
@Component
public class LectorTabular {

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
                case XLSX -> desdeCeldas(leerHoja(contenido), false);
                case CSV -> desdeCeldas(leerCsv(contenido), true);
                case JSON -> desdeArbol(json.readTree(contenido));
                case YAML -> desdeArbol(yaml.readTree(contenido));
            };
        } catch (ReglaNegocioException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // Solo el tipo de error y la posición: el mensaje de un parser puede citar el contenido, y el archivo puede
            // traer secretos (RNF11). Al usuario le basta saber que el contenido no corresponde al formato
            log.info("Archivo {} ilegible: {}{}", formato, ex.getClass().getSimpleName(), posicion(ex));
            throw new ReglaNegocioException("No se pudo leer el archivo como %s. Verifique que el contenido corresponda al formato"
                    .formatted(formato.name()));
        }
    }

    private record FilaCeldas(int numero, List<String> celdas) {
    }

    private static List<FilaCeldas> leerHoja(byte[] contenido) throws IOException {
        List<FilaCeldas> filas = new ArrayList<>();
        // POI rechaza por sí mismo los archivos con una tasa de compresión anómala (ZipSecureFile)
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

    /** UTF-8 estricto, si no lo es viene de Excel con la codificación regional */
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

    /** El separador más frecuente en la primera línea, coma si no hay ninguno */
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

    private static String posicion(Throwable ex) {
        if (ex instanceof JsonProcessingException j && j.getLocation() != null) {
            return " en la línea %d, columna %d".formatted(j.getLocation().getLineNr(), j.getLocation().getColumnNr());
        }
        return "";
    }

    /** desescapar: solo el CSV lleva el apóstrofo que neutraliza fórmulas (EscritorTabular); XLSX, JSON y YAML no */
    private static TablaArchivo desdeCeldas(List<FilaCeldas> filas, boolean desescapar) {
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
            Map<String, String> literales = new HashMap<>();
            for (int c = 0; c < Math.min(encabezado.size(), fila.celdas().size()); c++) {
                String crudo = fila.celdas().get(c);
                String literal = desescapar ? desescapar(crudo) : crudo;
                String valor = limpiar(literal);
                if (!encabezado.get(c).isEmpty() && valor != null) {
                    valores.put(encabezado.get(c), valor);
                    literales.put(encabezado.get(c), literal);
                }
            }
            registros.add(new FilaArchivo(fila.numero(), valores, literales, List.of()));
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
            Map<String, String> literales = new LinkedHashMap<>();
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
                        literales.put(clave, valor.asText());
                    }
                }
            }
            registros.add(new FilaArchivo(numero, valores, literales, List.copyOf(problemas)));
        }
        return new TablaArchivo(List.copyOf(columnas), registros);
    }

    static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }

    /**
     * Inverso exacto de EscritorTabular.escaparCsv: retira un apóstrofo solo si precede a lo que el escritor escapa (un
     * inicio de fórmula u otro apóstrofo). Así un valor que ya empezaba con apóstrofo vuelve igual, y un CSV escrito a
     * mano conserva el apóstrofo que precede a texto común.
     */
    static String desescapar(String valor) {
        if (valor != null && valor.length() > 1 && valor.charAt(0) == '\''
                && EscritorTabular.requiereEscape(valor.substring(1))) {
            return valor.substring(1);
        }
        return valor;
    }

    private static void exigirTope(int registros) {
        if (registros > MAXIMO_REGISTROS) {
            throw new ReglaNegocioException("El archivo supera el máximo de %d registros por importación"
                    .formatted(MAXIMO_REGISTROS));
        }
    }
}
