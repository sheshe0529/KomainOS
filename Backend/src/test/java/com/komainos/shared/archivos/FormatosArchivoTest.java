package com.komainos.shared.archivos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.shared.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Lectura y escritura de archivos de intercambio (RF12, RF13)")
class FormatosArchivoTest {

    private static final List<ColumnaArchivo> COLUMNAS = List.of(
            new ColumnaArchivo("hostname", "Hostname"),
            new ColumnaArchivo("direccion_ip", "Dirección IP"),
            new ColumnaArchivo("descripcion", "Descripción"),
            new ColumnaArchivo("vlan", "VLAN"));

    private final ObjectMapper json = new ObjectMapper();
    private final EscritorTabular escritor = new EscritorTabular(json);
    private final LectorTabular lector = new LectorTabular(json);

    private static Map<String, ?> fila(String hostname, String ip, String descripcion, String vlan) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("hostname", hostname);
        fila.put("direccion_ip", ip);
        fila.put("descripcion", descripcion);
        fila.put("vlan", vlan);
        return fila;
    }

    @ParameterizedTest
    @EnumSource(FormatoArchivo.class)
    @DisplayName("lo exportado se vuelve a leer igual: tildes, comas, comillas, fórmulas y celdas vacías")
    void idaYVuelta(FormatoArchivo formato) {
        List<Map<String, ?>> filas = List.of(
                fila("srv-app-01", "10.0.0.1", "=HIPERVINCULO(\"x\")", "220"),
                fila("srv-db-01", "10.0.0.2", "Réplica \"principal\", sede Lima", null));

        TablaArchivo tabla = lector.leer(formato, escritor.escribir(formato, COLUMNAS, filas, "Servidores"));

        assertThat(tabla.columnas()).containsExactly("hostname", "direccion_ip", "descripcion", "vlan");
        assertThat(tabla.filas()).hasSize(2);
        FilaArchivo primera = tabla.filas().get(0);
        FilaArchivo segunda = tabla.filas().get(1);
        assertThat(primera.valor("descripcion")).isEqualTo("=HIPERVINCULO(\"x\")");
        assertThat(primera.valor("vlan")).isEqualTo("220");
        assertThat(segunda.valor("descripcion")).isEqualTo("Réplica \"principal\", sede Lima");
        assertThat(segunda.valores()).doesNotContainKey("vlan");
        // El número es el que el usuario ve: fila de la hoja o posición del registro.
        int primeraFila = formato == FormatoArchivo.XLSX || formato == FormatoArchivo.CSV ? 2 : 1;
        assertThat(primera.numero()).isEqualTo(primeraFila);
        assertThat(segunda.numero()).isEqualTo(primeraFila + 1);
    }

    @Test
    @DisplayName("CSV: neutraliza fórmulas al exportar y escribe BOM para que Excel reconozca UTF-8")
    void csvSeguro() {
        byte[] contenido = escritor.escribir(FormatoArchivo.CSV, COLUMNAS,
                List.of(fila("srv-01", "10.0.0.1", "=1+1", null)), "Servidores");
        String texto = new String(contenido, StandardCharsets.UTF_8);

        assertThat(texto).startsWith("\uFEFFHostname,").contains("Dirección IP");
        assertThat(texto).contains("'=1+1");
    }

    @Test
    @DisplayName("CSV de Excel en español: punto y coma, Windows-1252 y filas en blanco")
    void csvRegional() {
        String texto = "Hostname;DIRECCIÓN IP;Descripción\r\nsrv-01;10.0.0.1;Réplica\r\n;;\r\nsrv-02;10.0.0.2;\r\n";

        TablaArchivo tabla = lector.leer(FormatoArchivo.CSV, texto.getBytes(Charset.forName("windows-1252")));

        assertThat(tabla.columnas()).containsExactly("hostname", "direccion_ip", "descripcion");
        assertThat(tabla.filas()).extracting(FilaArchivo::numero).containsExactly(2, 4);
        assertThat(tabla.filas().getFirst().valor("descripcion")).isEqualTo("Réplica");
    }

    @Test
    @DisplayName("JSON: acepta un objeto contenedor y marca los registros mal formados sin descartarlos")
    void jsonConProblemas() {
        String texto = """
                {"servidores": [
                  {"hostname": "srv-01", "direccionIp": "10.0.0.1", "etiquetas": ["a"]},
                  5
                ]}""";

        TablaArchivo tabla = lector.leer(FormatoArchivo.JSON, texto.getBytes(StandardCharsets.UTF_8));

        assertThat(tabla.filas().get(0).valor("direccion_ip")).isEqualTo("10.0.0.1");
        assertThat(tabla.filas().get(0).problemas()).singleElement().asString().contains("etiquetas");
        assertThat(tabla.filas().get(1).problemas()).singleElement().asString().contains("no es un objeto");
    }

    @Test
    @DisplayName("rechaza encabezados repetidos, contenido ilegible y formatos no admitidos")
    void archivosInvalidos() {
        byte[] repetido = "Hostname,hostname\na,b\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> lector.leer(FormatoArchivo.CSV, repetido))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("más de una vez");
        byte[] noEsExcel = "esto no es un libro".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> lector.leer(FormatoArchivo.XLSX, noEsExcel))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("como XLSX");
        byte[] jsonRoto = "[{\"hostname\": ".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> lector.leer(FormatoArchivo.JSON, jsonRoto))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("como JSON");
        assertThatThrownBy(() -> FormatoArchivo.deNombreArchivo("inventario.txt"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(FormatoArchivo.deNombreArchivo("C:/datos/Inventario.YML")).isEqualTo(FormatoArchivo.YAML);
    }

    @Test
    @DisplayName("normaliza encabezados escritos de distintas formas a la misma clave")
    void normalizaEncabezados() {
        assertThat(ClaveColumna.normalizar("Dirección IP")).isEqualTo("direccion_ip");
        assertThat(ClaveColumna.normalizar("direccionIp")).isEqualTo("direccion_ip");
        assertThat(ClaveColumna.normalizar(" DIRECCION  IP ")).isEqualTo("direccion_ip");
        assertThat(ClaveColumna.normalizar("\uFEFFHostname")).isEqualTo("hostname");
        assertThat(ClaveColumna.normalizar("Versión")).isEqualTo("version");
    }
}
