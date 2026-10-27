package com.komainos.ejecucion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion.Conexion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Adaptador Ansible real (RF52, RF71, RNF09): Linux por SSH y Windows por WinRM, sin agentes. Cada operación usa un
 * directorio temporal privado con el inventario, el playbook, el script preparado y el callback, que se borra al
 * terminar. Los secretos solo se escriben en el inventario y en el archivo de la llave, ambos con permisos 0600.
 * Solo se instancia con komainos.automatizacion.modo=REAL y en un controlador Linux.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "komainos.automatizacion", name = "modo", havingValue = "REAL")
public class AnsibleReal implements PuertoAnsible {

    private static final FileAttribute<Set<PosixFilePermission>> DIRECTORIO_PRIVADO =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<Set<PosixFilePermission>> ARCHIVO_PRIVADO =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    private static final long REGISTRO_MAXIMO = 8L * 1024 * 1024;

    private final PuertoConexionAutomatizacion conexiones;
    private final ObjectMapper json;
    private final PropiedadesAutomatizacion propiedades;

    public AnsibleReal(PuertoConexionAutomatizacion conexiones, ObjectMapper json,
                       PropiedadesAutomatizacion propiedades) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IllegalStateException("El modo REAL requiere un controlador Linux con Ansible instalado. "
                    + "En Windows use KOMAINOS_AUTOMATIZACION_MODO=SIMULADO");
        }
        this.conexiones = conexiones;
        this.json = json;
        this.propiedades = propiedades;
    }

    @Override
    public ResultadoEjecucion ejecutar(SolicitudEjecucion solicitud) {
        String codigo = PreparadorScript.preparar(solicitud.familia(), solicitud.contenido(), solicitud.parametros())
                .contenido();
        ResultadoEjecucion resultado = invocar(solicitud, codigo);
        return solicitud.esAnalisis() ? ContratoAnalisis.aplicar(resultado, json) : resultado;
    }

    @Override
    public ResultadoEjecucion probarConexion(SolicitudEjecucion solicitud) {
        String recurso = solicitud.familia() == FamiliaSistemaOperativo.WINDOWS
                ? "/ansible/comprobaciones/acceso_windows.ps1"
                : "/ansible/comprobaciones/acceso_linux.sh";
        try {
            return invocar(solicitud, recurso(recurso));
        } catch (IOException ex) {
            return ResultadoEjecucion.fallo(false, TipoFallo.CONFIGURACION, "falta el script de comprobación de acceso");
        }
    }

    private ResultadoEjecucion invocar(SolicitudEjecucion s, String codigo) {
        Instant inicio = Instant.now();
        Conexion conexion;
        try {
            conexion = conexiones.resolver(s.idCuentaServicio(), s.familia());
        } catch (RuntimeException ex) {
            return ResultadoEjecucion.fallo(false, TipoFallo.CONFIGURACION,
                    "la cuenta de servicio no está disponible: " + ex.getMessage());
        }
        Path directorio = null;
        try {
            directorio = Files.createTempDirectory("komainos-ansible-", DIRECTORIO_PRIVADO);
            Files.createDirectory(directorio.resolve("tmp"), DIRECTORIO_PRIVADO);
            ResultadoEjecucion resultado = ejecutarEn(directorio, s, codigo, conexion);
            log.info("Ansible {} en {}: {} ({} s)", s.proposito(), s.hostname(),
                    resultado.correcto() ? "correcto" : resultado.fallo(),
                    Duration.between(inicio, Instant.now()).toSeconds());
            return resultado;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ResultadoEjecucion.fallo(false, TipoFallo.EJECUCION, "la operación se interrumpió");
        } catch (IOException ex) {
            // Sin el mensaje de la excepción: podría incluir rutas con material sensible
            log.warn("No se pudo preparar la ejecución de Ansible en {}: {}", s.hostname(), ex.getClass().getSimpleName());
            return ResultadoEjecucion.fallo(false, TipoFallo.CONFIGURACION,
                    "no se pudieron preparar los archivos temporales de la ejecución en el controlador");
        } finally {
            borrar(directorio);
        }
    }

    private ResultadoEjecucion ejecutarEn(Path directorio, SolicitudEjecucion s, String codigo, Conexion conexion)
            throws IOException, InterruptedException {
        PropiedadesAutomatizacion.Ansible config = propiedades.ansible();
        boolean windows = s.familia() == FamiliaSistemaOperativo.WINDOWS;
        // Windows PowerShell 5.1 lee como ANSI un .ps1 sin BOM y corrompe las tildes de los mensajes
        Path script = escribir(directorio, PreparadorScript.nombreArchivo(s.familia()), windows ? "﻿" + codigo : codigo);
        Path llave = conexion.llave() ? escribir(directorio, "llave_privada", normalizarLlave(conexion.secreto())) : null;
        Path inventario = escribir(directorio, "inventario.json", json.writeValueAsString(
                ArtefactosAnsible.inventario(s.direccionIp(), s.familia(), conexion, llave, config)));
        Path playbook = escribir(directorio, "playbook.json", json.writeValueAsString(
                ArtefactosAnsible.playbook(script, s.familia(), s.maxMinutos())));
        escribir(directorio, ArtefactosAnsible.CALLBACK + ".py", recurso("/ansible/callback_plugins/komainos_json.py"));

        Path registro = directorio.resolve("registro.log");
        ProcessBuilder constructor = new ProcessBuilder(config.ejecutable(), "-i", inventario.toString(),
                playbook.toString())
                .directory(directorio.toFile())
                .redirectErrorStream(true)
                .redirectOutput(registro.toFile());
        constructor.environment().putAll(ArtefactosAnsible.entorno(directorio, config));

        Process proceso;
        try {
            proceso = constructor.start();
        } catch (IOException ex) {
            return ResultadoEjecucion.fallo(false, TipoFallo.CONFIGURACION,
                    "no se encontró «%s» en el controlador; instale Ansible o ajuste KOMAINOS_ANSIBLE_EJECUTABLE"
                            .formatted(config.ejecutable()));
        }
        // Margen sobre el timeout de la tarea, que Ansible aplica primero
        if (!proceso.waitFor(Math.max(1, s.maxMinutos()) + 1L, TimeUnit.MINUTES)) {
            detener(proceso);
            return ResultadoEjecucion.fallo(false, TipoFallo.TIEMPO_EXCEDIDO,
                    "la operación superó %d minutos y se canceló".formatted(s.maxMinutos()));
        }
        if (Files.size(registro) > REGISTRO_MAXIMO) {
            return ResultadoEjecucion.fallo(false, TipoFallo.EJECUCION,
                    "la salida supera el tamaño permitido de 8 MB");
        }
        return InterpreteAnsible.interpretar(Files.readString(registro, StandardCharsets.UTF_8), proceso.exitValue(),
                s.proposito(), json, conexion.secreto(), conexion.secretoSu());
    }

    /** ssh rechaza una llave con retornos de carro o sin salto de línea final («invalid format») */
    static String normalizarLlave(String llave) {
        String normalizada = llave.replace("\r\n", "\n").replace('\r', '\n').strip();
        return normalizada + "\n";
    }

    private static Path escribir(Path directorio, String nombre, String texto) throws IOException {
        Path archivo = Files.createFile(directorio.resolve(nombre), ARCHIVO_PRIVADO);
        return Files.writeString(archivo, texto, StandardCharsets.UTF_8);
    }

    private String recurso(String ruta) throws IOException {
        try (InputStream entrada = getClass().getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IOException("Recurso inexistente: " + ruta);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void detener(Process proceso) throws InterruptedException {
        proceso.descendants().forEach(ProcessHandle::destroy);
        proceso.destroy();
        if (!proceso.waitFor(5, TimeUnit.SECONDS)) {
            proceso.descendants().forEach(ProcessHandle::destroyForcibly);
            proceso.destroyForcibly();
        }
    }

    /** El directorio contiene la contraseña o la llave: se borra aunque la ejecución haya fallado */
    private static void borrar(Path directorio) {
        if (directorio == null) {
            return;
        }
        try (Stream<Path> rutas = Files.walk(directorio)) {
            List<Path> ordenadas = rutas.sorted(Comparator.reverseOrder()).toList();
            for (Path ruta : ordenadas) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException ex) {
            log.error("No se pudo borrar el directorio temporal de Ansible {}: bórrelo manualmente", directorio);
        }
    }
}
