package com.komainos.ejecucion;

import com.komainos.ejecucion.model.ParametroEjecucion;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.ejecucion.service.PreparadorScript;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.TipoDato;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Contrato de parámetros de los scripts (RF25, RF52)")
class PreparadorScriptTest {

    private static final List<ParametroEjecucion> PARAMETROS = List.of(
            new ParametroEjecucion("RUTA", TipoDato.STRING, "/tmp/it's"),
            new ParametroEjecucion("DIAS", TipoDato.INTEGER, "7"),
            new ParametroEjecucion("FACTOR", TipoDato.DECIMAL, "1.50"),
            new ParametroEjecucion("SIMULAR", TipoDato.BOOLEAN, "TRUE"),
            new ParametroEjecucion("OPCIONAL", TipoDato.STRING, null));

    @Nested
    @DisplayName("Bash")
    class Bash {

        @Test
        @DisplayName("Conserva el shebang y define cada parámetro como variable antes del código")
        void bloqueDespuesDelShebang() {
            ScriptPreparado s = PreparadorScript.preparar(FamiliaSistemaOperativo.LINUX,
                    "#!/bin/bash\r\nset -eu\r\necho \"$RUTA\"", PARAMETROS);

            assertThat(s.nombreArchivo()).isEqualTo("tarea.sh");
            assertThat(s.contenido().lines().toList()).containsExactly(
                    "#!/bin/bash",
                    PreparadorScript.INICIO_BLOQUE,
                    "RUTA='/tmp/it'\"'\"'s'",
                    "DIAS=7",
                    "FACTOR=1.50",
                    "SIMULAR=true",
                    "OPCIONAL=''",
                    PreparadorScript.FIN_BLOQUE,
                    "set -eu",
                    "echo \"$RUTA\"");
            assertThat(s.lineaInicioParametros()).isEqualTo(2);
            assertThat(s.lineaFinParametros()).isEqualTo(8);
        }

        @Test
        @DisplayName("Un valor con comillas o $( ) no se interpreta como código")
        void valoresLiterales() {
            String literal = PreparadorScript.literal(FamiliaSistemaOperativo.LINUX,
                    new ParametroEjecucion("X", TipoDato.STRING, "a'b; $(id)"));

            assertThat(literal).isEqualTo("'a'\"'\"'b; $(id)'");
        }

        @Test
        @DisplayName("Sin parámetros solo agrega el shebang si falta y quita los retornos de carro")
        void sinParametros() {
            ScriptPreparado s = PreparadorScript.preparar(FamiliaSistemaOperativo.LINUX, "df -h\r\n", List.of());

            assertThat(s.contenido()).isEqualTo("#!/usr/bin/env bash\ndf -h\n");
            assertThat(s.lineaInicioParametros()).isZero();
        }
    }

    @Nested
    @DisplayName("PowerShell")
    class PowerShell {

        @Test
        @DisplayName("Usa literales de PowerShell y deja las sentencias using al principio")
        void literalesYUsing() {
            ScriptPreparado s = PreparadorScript.preparar(FamiliaSistemaOperativo.WINDOWS,
                    "# Comentario\nusing namespace System.IO\nWrite-Output $RUTA", PARAMETROS);

            assertThat(s.nombreArchivo()).isEqualTo("tarea.ps1");
            assertThat(s.contenido().lines().toList()).containsExactly(
                    "# Comentario",
                    "using namespace System.IO",
                    PreparadorScript.INICIO_BLOQUE,
                    "$RUTA = '/tmp/it''s'",
                    "$DIAS = 7",
                    "$FACTOR = [decimal]'1.50'",
                    "$SIMULAR = $true",
                    "$OPCIONAL = $null",
                    PreparadorScript.FIN_BLOQUE,
                    "Write-Output $RUTA");
            assertThat(s.lineaInicioParametros()).isEqualTo(3);
        }

        @Test
        @DisplayName("Un bloque param() inicial se rechaza solo si la versión declara parámetros")
        void bloqueParam() {
            String codigo = "<# Ayuda #>\n[CmdletBinding()]\nparam([int]$Dias = 7)\nWrite-Output $Dias";

            assertThat(PreparadorScript.problemaContenido(FamiliaSistemaOperativo.WINDOWS, codigo, true))
                    .hasValueSatisfying(m -> assertThat(m).contains("param()"));
            assertThat(PreparadorScript.problemaContenido(FamiliaSistemaOperativo.WINDOWS, codigo, false)).isEmpty();
            assertThat(PreparadorScript.problemaContenido(FamiliaSistemaOperativo.LINUX, codigo, true)).isEmpty();
        }

        @Test
        @DisplayName("Sin parámetros el script se ejecuta tal cual")
        void sinParametros() {
            assertThat(PreparadorScript.preparar(FamiliaSistemaOperativo.WINDOWS, "Get-Date", List.of()).contenido())
                    .isEqualTo("Get-Date");
        }
    }

    @Nested
    @DisplayName("Validación de nombres y valores")
    class Validacion {

        @Test
        @DisplayName("Rechaza nombres que no son variables o que reemplazan variables del intérprete")
        void nombres() {
            assertThat(PreparadorScript.problemaNombre("DIAS_RETENCION")).isEmpty();
            assertThat(PreparadorScript.problemaNombre("x;comando")).isPresent();
            assertThat(PreparadorScript.problemaNombre("1ARCHIVO")).isPresent();
            assertThat(PreparadorScript.problemaNombre("path")).isPresent();
            assertThat(PreparadorScript.problemaNombre("PSItem")).isPresent();
            assertThat(PreparadorScript.problemaNombre("KOMAINOS_TOKEN")).isPresent();
        }

        @Test
        @DisplayName("El valor por defecto debe corresponder al tipo declarado")
        void valores() {
            assertThat(PreparadorScript.problemaValor("D", TipoDato.INTEGER, "7")).isEmpty();
            assertThat(PreparadorScript.problemaValor("D", TipoDato.INTEGER, "7.5")).isPresent();
            assertThat(PreparadorScript.problemaValor("F", TipoDato.DECIMAL, "2.75")).isEmpty();
            assertThat(PreparadorScript.problemaValor("F", TipoDato.DECIMAL, "1e9")).isPresent();
            assertThat(PreparadorScript.problemaValor("B", TipoDato.BOOLEAN, "false")).isEmpty();
            assertThat(PreparadorScript.problemaValor("B", TipoDato.BOOLEAN, "si")).isPresent();
            assertThat(PreparadorScript.problemaValor("S", TipoDato.STRING, null)).isEmpty();
        }
    }
}
