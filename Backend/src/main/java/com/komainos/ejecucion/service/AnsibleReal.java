package com.komainos.ejecucion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion.Conexion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Adaptador Ansible real (RF52, RF71, RNF09): Linux por SSH y Windows por WinRM, sin agentes. Cada operación usa un
 * directorio temporal privado con el inventario, el playbook, el script preparado y el callback, que se borra al
 * terminar. Los secretos solo se escriben en el inventario y en el archivo de la llave, ambos con permisos 0600.
 * Requiere un controlador Linux con Ansible (la imagen Docker del despliegue lo trae). En otro sistema operativo el
 * backend arranca igual, pero cada operación remota falla como error de configuración del controlador.
 */
@Service
@Slf4j
public class AnsibleReal implements PuertoAnsible {

    private static final FileAttribute<Set<PosixFilePermission>> DIRECTORIO_PRIVADO =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<Set<PosixFilePermission>> ARCHIVO_PRIVADO =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    private static final int REGISTRO_MAXIMO = 8 * 1024 * 1024;

    private final PuertoConexionAutomatizacion conexiones;
    private final ObjectMapper json;
    private final PropiedadesAutomatizacion propiedades;

    public AnsibleReal(PuertoConexionAutomatizacion conexiones, ObjectMapper json,
                       PropiedadesAutomatizacion propiedades) {
        this.conexiones = conexiones;
        this.json = json;
        this.propiedades = propiedades;
        if (!controladorCompatible()) {
            log.warn("El controlador es {}: las operaciones remotas fallarán hasta ejecutar el backend en Linux con "
                    + "Ansible", System.getProperty("os.name", "desconocido"));
        }
    }

    /** Ansible solo corre en sistemas POSIX y el directorio privado de cada ejecución exige permisos POSIX */
    static boolean controladorCompatible() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    @Override
    public ResultadoEjecucion ejecutar(SolicitudEjecucion solicitud) {
        String codigo = PreparadorScript.preparar(solicitud.familia(), solicitud.contenido(), solicitud.parametros(),
                        solicitud.listas())
                .contenido();
        ResultadoEjecucion resultado = invocar(solicitud, codigo);
        return solicitud.esAnalisis() ? ContratoAnalisis.aplicar(resultado, json) : resultado;
    }

    @Override
    public ResultadoEjecucion probarConexion(SolicitudEjecucion solicitud) {
        try {
            return invocar(solicitud, comprobacion(solicitud.proposito(), solicitud.familia()));
        } catch (IOException ex) {
            return ResultadoEjecucion.fallo(TipoFallo.CONFIGURACION, "falta el script de comprobación de acceso");
        }
    }

    /**
     * Script de la prueba de acceso: el mismo para las dos finalidades, porque la elevación ya la decide la cuenta e
     * informa los permisos efectivos sin exigirlos (DEC-57). Sin retornos de carro: una copia del repositorio en Windows
     * los agrega al empaquetar y bash no ejecuta un script con CRLF.
     */
    static String comprobacion(PropositoEjecucion proposito, FamiliaSistemaOperativo familia) throws IOException {
        String ruta = "/ansible/comprobaciones/acceso_"
                + (familia == FamiliaSistemaOperativo.WINDOWS ? "windows.ps1" : "linux.sh");
        return recurso(ruta).replace("\r\n", "\n");
    }

    private ResultadoEjecucion invocar(SolicitudEjecucion s, String codigo) {
        // Antes de resolver la cuenta: en un controlador incompatible no tiene sentido descifrar el secreto
        if (!controladorCompatible()) {
            return ResultadoEjecucion.fallo(TipoFallo.CONFIGURACION, ("el controlador es %s; ejecute el backend en "
                    + "Linux con Ansible, por ejemplo con la imagen Docker del despliegue")
                    .formatted(System.getProperty("os.name", "desconocido")));
        }
        Instant inicio = Instant.now();
        Conexion conexion;
        try {
            conexion = conexiones.resolver(s.idCuentaServicio(), s.familia(), s.proposito());
        } catch (RuntimeException ex) {
            return ResultadoEjecucion.fallo(TipoFallo.CONFIGURACION,
                    "la cuenta de servicio no está disponible: " + ex.getMessage());
        }
        Path directorio = null;
        try {
            directorio = Files.createTempDirectory("komainos-ansible-", DIRECTORIO_PRIVADO);
            Files.createDirectory(directorio.resolve("tmp"), DIRECTORIO_PRIVADO);
            ResultadoEjecucion resultado = ejecutarEn(directorio, s, codigo, conexion);
            log.info("Ansible {} en {}{}: {} ({} s)", s.proposito(), s.hostname(),
                    s.idEvaluacion() == null ? "" : " (evaluación " + s.idEvaluacion() + ")",
                    resultado.correcto() ? "correcto" : resultado.fallo(),
                    Duration.between(inicio, Instant.now()).toSeconds());
            return resultado;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ResultadoEjecucion.fallo(TipoFallo.EJECUCION, "la operación se interrumpió");
        } catch (IOException ex) {
            // Sin el mensaje de la excepción: podría incluir rutas con material sensible
            log.warn("No se pudo preparar la ejecución de Ansible en {}: {}", s.hostname(), ex.getClass().getSimpleName());
            return ResultadoEjecucion.fallo(TipoFallo.CONFIGURACION,
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
                ArtefactosAnsible.inventario(s.direccionIp(), s.familia(), conexion,
                        llave == null ? null : llave.toString(), config)));
        Path playbook = escribir(directorio, "playbook.json", json.writeValueAsString(
                ArtefactosAnsible.playbook(script, s.familia(), s.maxMinutos())));
        escribir(directorio, ArtefactosAnsible.CALLBACK + ".py", recurso("/ansible/callback_plugins/komainos_json.py"));

        ProcessBuilder constructor = new ProcessBuilder(config.ejecutable(), "-i", inventario.toString(),
                playbook.toString())
                .directory(directorio.toFile())
                .redirectErrorStream(true);
        constructor.environment().putAll(ArtefactosAnsible.entorno(directorio, config));

        Process proceso;
        try {
            proceso = constructor.start();
        } catch (IOException ex) {
            return ResultadoEjecucion.fallo(TipoFallo.CONFIGURACION,
                    "no se encontró «%s» en el controlador; instale Ansible o ajuste KOMAINOS_ANSIBLE_EJECUTABLE"
                            .formatted(config.ejecutable()));
        }
        ProcesoAnsible.Salida salida;
        try {
            // Margen sobre el timeout de la tarea, que Ansible aplica primero
            salida = ProcesoAnsible.esperar(proceso, Duration.ofMinutes(Math.max(1, s.maxMinutos()) + 1L), REGISTRO_MAXIMO);
        } catch (TimeoutException ex) {
            return ResultadoEjecucion.fallo(TipoFallo.TIEMPO_EXCEDIDO,
                    "la operación superó %d minutos y se canceló".formatted(s.maxMinutos()));
        } catch (ProcesoAnsible.SalidaExcedida ex) {
            return ResultadoEjecucion.fallo(TipoFallo.EJECUCION,
                    "la salida supera el tamaño permitido de 8 MB");
        }
        return InterpreteAnsible.interpretar(salida.texto(), salida.codigo(),
                s.proposito(), json, conexion.secreto(), conexion.secretoElevacion());
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

    private static String recurso(String ruta) throws IOException {
        try (InputStream entrada = AnsibleReal.class.getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IOException("Recurso inexistente: " + ruta);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
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
