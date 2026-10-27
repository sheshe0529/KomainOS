package com.komainos.ejecucion.service;

import com.komainos.ejecucion.model.ParametroEjecucion;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.TipoDato;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contrato de parámetros de los scripts (RF25, RF52): KomainOS antepone al código de la versión un bloque que define
 * cada parámetro como una variable del mismo nombre, {@code $NOMBRE} en Bash y en PowerShell. El mismo texto se ejecuta
 * en el servidor y se muestra en la vista previa del panel, así un script se puede probar a mano con idéntico resultado.
 * Los valores van entre comillas simples con su escape: un valor nunca se interpreta como código.
 */
public final class PreparadorScript {

    public static final String PATRON_NOMBRE = "[A-Za-z_][A-Za-z0-9_]{0,63}";
    public static final String INICIO_BLOQUE = "# ---- KomainOS: parámetros de ejecución ----";
    public static final String FIN_BLOQUE = "# ---- Fin de los parámetros ----";
    private static final int LARGO_MAXIMO_VALOR = 1000;

    /** Variables del intérprete que un parámetro no puede reemplazar sin romper el script (Bash y PowerShell) */
    private static final Set<String> RESERVADOS = Set.of(
            "BASH", "BASHOPTS", "BASHPID", "BASH_ENV", "BASH_VERSION", "CDPATH", "COLUMNS", "DIRSTACK", "EUID",
            "GROUPS", "HISTFILE", "HISTSIZE", "HOME", "HOSTNAME", "HOSTTYPE", "IFS", "LANG", "LANGUAGE", "LC_ALL",
            "LINENO", "LINES", "LOGNAME", "MACHTYPE", "MAIL", "OLDPWD", "OPTARG", "OPTERR", "OPTIND", "OSTYPE", "PATH",
            "PIPESTATUS", "PPID", "PS1", "PS2", "PS3", "PS4", "PWD", "RANDOM", "REPLY", "SECONDS", "SHELL",
            "SHELLOPTS", "SHLVL", "TERM", "TMOUT", "TMPDIR", "UID", "USER", "_",
            "ARGS", "ENV", "ERROR", "EVENT", "EVENTARGS", "EXECUTIONCONTEXT", "FALSE", "FOREACH", "HOST", "INPUT",
            "ISCORECLR", "ISLINUX", "ISMACOS", "ISWINDOWS", "LASTEXITCODE", "MATCHES", "MYINVOCATION",
            "NESTEDPROMPTLEVEL", "NULL", "OFS", "PID", "PROFILE", "PSBOUNDPARAMETERS", "PSCMDLET", "PSCOMMANDPATH",
            "PSCULTURE", "PSDEBUGCONTEXT", "PSEDITION", "PSHOME", "PSITEM", "PSSCRIPTROOT", "PSSENDERINFO",
            "PSUICULTURE", "PSVERSIONTABLE", "SENDER", "SHELLID", "STACKTRACE", "SWITCH", "THIS", "TRUE",
            "CONFIRMPREFERENCE", "DEBUGPREFERENCE", "ERRORACTIONPREFERENCE", "INFORMATIONPREFERENCE",
            "PROGRESSPREFERENCE", "VERBOSEPREFERENCE", "WARNINGPREFERENCE", "WHATIFPREFERENCE");

    /** Un bloque param() al inicio choca con las variables que define KomainOS */
    private static final Pattern BLOQUE_PARAM = Pattern.compile("^param\\s*\\(", Pattern.CASE_INSENSITIVE);

    private PreparadorScript() {
    }

    public static ScriptPreparado preparar(FamiliaSistemaOperativo familia, String contenido,
                                           List<ParametroEjecucion> parametros) {
        List<String> lineas = new ArrayList<>(List.of(normalizar(contenido).split("\n", -1)));
        return familia == FamiliaSistemaOperativo.WINDOWS
                ? prepararPowerShell(lineas, parametros)
                : prepararBash(lineas, parametros);
    }

    public static String nombreArchivo(FamiliaSistemaOperativo familia) {
        return familia == FamiliaSistemaOperativo.WINDOWS ? "tarea.ps1" : "tarea.sh";
    }

    public static String lenguaje(FamiliaSistemaOperativo familia) {
        return familia == FamiliaSistemaOperativo.WINDOWS ? "powershell" : "bash";
    }

    /** Vacío si el nombre sirve como variable en Bash y en PowerShell */
    public static Optional<String> problemaNombre(String nombre) {
        if (nombre == null || !nombre.matches(PATRON_NOMBRE)) {
            return Optional.of(("El parámetro «%s» no es un nombre de variable válido: use letras sin tilde, números "
                    + "y guion bajo, sin empezar por un número").formatted(nombre));
        }
        String mayusculas = nombre.toUpperCase(Locale.ROOT);
        if (RESERVADOS.contains(mayusculas)) {
            return Optional.of("El parámetro «%s» reemplazaría una variable del intérprete: elija otro nombre"
                    .formatted(nombre));
        }
        if (mayusculas.startsWith("KOMAINOS_")) {
            return Optional.of("El prefijo KOMAINOS_ está reservado para el sistema: elija otro nombre");
        }
        return Optional.empty();
    }

    /** Vacío si el valor corresponde al tipo declarado del parámetro */
    public static Optional<String> problemaValor(String nombre, TipoDato tipo, String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        if (valor.length() > LARGO_MAXIMO_VALOR || valor.indexOf('\0') >= 0) {
            return Optional.of("El valor de %s supera los %d caracteres o contiene caracteres no permitidos"
                    .formatted(nombre, LARGO_MAXIMO_VALOR));
        }
        boolean valido = switch (tipo) {
            case INTEGER -> valor.trim().matches("-?\\d{1,18}");
            case DECIMAL -> esDecimal(valor.trim());
            case BOOLEAN -> valor.trim().equalsIgnoreCase("true") || valor.trim().equalsIgnoreCase("false");
            case STRING -> true;
        };
        return valido ? Optional.empty() : Optional.of(switch (tipo) {
            case INTEGER -> "El valor de %s debe ser un número entero".formatted(nombre);
            case DECIMAL -> "El valor de %s debe ser un número (use punto decimal)".formatted(nombre);
            case BOOLEAN -> "El valor de %s debe ser true o false".formatted(nombre);
            case STRING -> "";
        });
    }

    /**
     * Vacío si el contenido es compatible con el bloque de parámetros. En PowerShell un param() inicial se rechaza solo
     * si la versión declara parámetros: sin ellos el script se ejecuta tal cual y su param() conserva sus valores.
     */
    public static Optional<String> problemaContenido(FamiliaSistemaOperativo familia, String contenido,
                                                     boolean declaraParametros) {
        if (familia != FamiliaSistemaOperativo.WINDOWS || !declaraParametros) {
            return Optional.empty();
        }
        return primeraSentenciaPowerShell(normalizar(contenido))
                .filter(sentencia -> BLOQUE_PARAM.matcher(sentencia).find())
                .map(s -> "El script declara un bloque param(): KomainOS ya define cada parámetro como variable "
                        + "($NOMBRE) antes del código. Elimine el bloque param() y use las variables directamente.");
    }

    /** Texto con el que el parámetro aparece en el bloque, por ejemplo {@code 'C:\Temp'} o {@code $true} */
    public static String literal(FamiliaSistemaOperativo familia, ParametroEjecucion p) {
        boolean windows = familia == FamiliaSistemaOperativo.WINDOWS;
        String valor = p.valor();
        if (valor == null) {
            return windows ? "$null" : "''";
        }
        return switch (p.tipo()) {
            case INTEGER -> new BigInteger(valor.trim()).toString();
            case DECIMAL -> windows
                    ? "[decimal]'" + new BigDecimal(valor.trim()).toPlainString() + "'"
                    : new BigDecimal(valor.trim()).toPlainString();
            case BOOLEAN -> {
                boolean verdadero = valor.trim().equalsIgnoreCase("true");
                yield windows ? (verdadero ? "$true" : "$false") : (verdadero ? "true" : "false");
            }
            case STRING -> windows
                    ? "'" + valor.replace("'", "''") + "'"
                    : "'" + valor.replace("'", "'\"'\"'") + "'";
        };
    }

    private static ScriptPreparado prepararBash(List<String> lineas, List<ParametroEjecucion> parametros) {
        List<String> resultado = new ArrayList<>();
        // El controlador ejecuta con /bin/bash; el shebang solo importa al probar el archivo a mano
        if (!lineas.isEmpty() && lineas.getFirst().startsWith("#!")) {
            resultado.add(lineas.removeFirst());
        } else {
            resultado.add("#!/usr/bin/env bash");
        }
        int inicio = 0;
        int fin = 0;
        if (!parametros.isEmpty()) {
            inicio = resultado.size() + 1;
            resultado.add(INICIO_BLOQUE);
            for (ParametroEjecucion p : parametros) {
                resultado.add(p.nombre() + "=" + literal(FamiliaSistemaOperativo.LINUX, p));
            }
            resultado.add(FIN_BLOQUE);
            fin = resultado.size();
        }
        resultado.addAll(lineas);
        return new ScriptPreparado("tarea.sh", "bash", String.join("\n", resultado), inicio, fin);
    }

    /** Las sentencias using deben ser las primeras del script: el bloque va después de ellas */
    private static ScriptPreparado prepararPowerShell(List<String> lineas, List<ParametroEjecucion> parametros) {
        if (parametros.isEmpty()) {
            return new ScriptPreparado("tarea.ps1", "powershell", String.join("\n", lineas), 0, 0);
        }
        int posicion = 0;
        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i).trim();
            if (linea.regionMatches(true, 0, "using ", 0, 6)) {
                posicion = i + 1;
            } else if (!linea.isEmpty() && !linea.startsWith("#")) {
                break;
            }
        }
        List<String> bloque = new ArrayList<>();
        bloque.add(INICIO_BLOQUE);
        for (ParametroEjecucion p : parametros) {
            bloque.add("$" + p.nombre() + " = " + literal(FamiliaSistemaOperativo.WINDOWS, p));
        }
        bloque.add(FIN_BLOQUE);
        List<String> resultado = new ArrayList<>(lineas.subList(0, posicion));
        resultado.addAll(bloque);
        resultado.addAll(lineas.subList(posicion, lineas.size()));
        return new ScriptPreparado("tarea.ps1", "powershell", String.join("\n", resultado),
                posicion + 1, posicion + bloque.size());
    }

    /** Primera sentencia después de comentarios, using y atributos como [CmdletBinding()] */
    private static Optional<String> primeraSentenciaPowerShell(String contenido) {
        String resto = contenido;
        while (true) {
            String recortado = resto.stripLeading();
            if (recortado.startsWith("<#")) {
                int cierre = recortado.indexOf("#>");
                resto = cierre < 0 ? "" : recortado.substring(cierre + 2);
            } else if (recortado.startsWith("#") || recortado.regionMatches(true, 0, "using ", 0, 6)) {
                int salto = recortado.indexOf('\n');
                resto = salto < 0 ? "" : recortado.substring(salto + 1);
            } else if (recortado.startsWith("[")) {
                int cierre = recortado.indexOf(']');
                resto = cierre < 0 ? "" : recortado.substring(cierre + 1);
            } else {
                return recortado.isEmpty() ? Optional.empty() : Optional.of(recortado);
            }
        }
    }

    /** Sin retornos de carro: Bash los toma como parte del comando */
    private static String normalizar(String contenido) {
        return contenido == null ? "" : contenido.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean esDecimal(String valor) {
        try {
            new BigDecimal(valor);
            return !valor.toLowerCase(Locale.ROOT).contains("e");
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
