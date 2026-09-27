package com.komainos.inventario.dominio.intercambio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.inventario.dominio.DatosServidor;
import com.komainos.inventario.dominio.Entorno;
import com.komainos.inventario.dominio.EstadoServidor;
import com.komainos.inventario.dominio.NivelCriticidad;
import com.komainos.inventario.dominio.ServicioCatalogos;
import com.komainos.inventario.dominio.ServicioServidor;
import com.komainos.inventario.dominio.Servidor;
import com.komainos.inventario.dominio.SistemaOperativo;
import com.komainos.inventario.dominio.VersionSistemaOperativo;
import com.komainos.inventario.dominio.intercambio.AnalisisImportacion.EstadoFila;
import com.komainos.inventario.dominio.intercambio.ResultadoImportacion.Resultado;
import com.komainos.inventario.infra.ServidorRepositorio;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.seguridad.infra.UsuarioRepositorio;
import com.komainos.shared.api.validacion.ValidadorDireccionIp;
import com.komainos.shared.archivos.ArchivoGenerado;
import com.komainos.shared.archivos.EscritorTabular;
import com.komainos.shared.archivos.FilaArchivo;
import com.komainos.shared.archivos.FormatoArchivo;
import com.komainos.shared.archivos.LectorTabular;
import com.komainos.shared.archivos.TablaArchivo;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.error.ConflictoException;
import com.komainos.shared.error.RecursoNoEncontradoException;
import com.komainos.shared.error.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.CLUSTER;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.CRITICIDAD;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.DATACENTER;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.DESCRIPCION;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.DIRECCION_IP;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.DNS;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.ENTORNO;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.HOSTNAME;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.PLATAFORMA;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.RESPONSABLE;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.SERVIDOR_FISICO;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.SISTEMA_OPERATIVO;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.VERSION;
import static com.komainos.inventario.dominio.intercambio.ColumnaInventario.VLAN;

/**
 * Importación masiva del inventario (RF12, HU08).
 *
 * <p>Se hace en dos pasos sin estado en el servidor: {@link #analizar} clasifica
 * cada registro en nuevo, duplicado o erróneo sin escribir nada (CA2), y
 * {@link #importar} recibe el mismo archivo junto con las filas duplicadas que
 * el usuario aceptó sobrescribir (CA4), vuelve a analizarlo con los datos del
 * momento y registra lo válido (CA3). Volver a analizar evita confiar en una
 * vista previa que pudo quedar desactualizada mientras el usuario la revisaba.
 *
 * <p>Cada registro se da de alta o se actualiza con los mismos casos de uso del
 * registro individual ({@link ServicioServidor}), en su propia transacción: un
 * registro rechazado no impide importar los demás.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioImportacionInventario {

    /** La misma regla que el alta individual ({@code ServidorPeticion}). */
    private static final Pattern PATRON_HOSTNAME = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$");

    private final LectorTabular lector;
    private final EscritorTabular escritor;
    private final ServidorRepositorio servidores;
    private final UsuarioRepositorio usuarios;
    private final ServicioCatalogos catalogos;
    private final ServicioServidor servicioServidor;
    private final ServicioAuditoria auditoria;
    private final PlatformTransactionManager transacciones;

    /** Archivo con las columnas importables y un registro vacío que muestra su forma. */
    public ArchivoGenerado plantilla(FormatoArchivo formato) {
        List<ColumnaInventario> columnas = ColumnaInventario.importables();
        Map<String, Object> vacio = new HashMap<>();
        byte[] contenido = escritor.escribir(formato,
                columnas.stream().map(ColumnaInventario::comoColumnaArchivo).toList(), List.of(vacio), "Servidores");
        return new ArchivoGenerado("plantilla_inventario_servidores." + formato.extension(), formato, contenido);
    }

    // ------------------------------------------------------------ vista previa

    /** HU08 CA2: clasifica los registros sin modificar el inventario. */
    public AnalisisImportacion analizar(String nombreArchivo, byte[] contenido) {
        FormatoArchivo formato = FormatoArchivo.deNombreArchivo(nombreArchivo);
        TablaArchivo tabla = lector.leer(formato, contenido);
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        return lectura.execute(estado -> clasificar(formato, tabla));
    }

    private AnalisisImportacion clasificar(FormatoArchivo formato, TablaArchivo tabla) {
        List<ColumnaInventario> reconocidas = new ArrayList<>();
        List<String> ignoradas = new ArrayList<>();
        for (String clave : tabla.columnas()) {
            ColumnaInventario columna = ColumnaInventario.deClave(clave);
            if (columna != null && columna.importable()) {
                reconocidas.add(columna);
            } else {
                ignoradas.add(columna != null ? columna.etiqueta() : clave);
            }
        }
        List<String> faltantes = ColumnaInventario.importables().stream()
                .filter(c -> c.obligatoria() && !reconocidas.contains(c))
                .map(ColumnaInventario::etiqueta)
                .toList();
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException(("Faltan columnas obligatorias: %s. Descargue la plantilla para ver el "
                    + "formato esperado").formatted(String.join(", ", faltantes)));
        }

        Set<ColumnaInventario> presentes = EnumSet.copyOf(reconocidas);
        Referencias referencias = cargarReferencias(tabla);
        Map<String, Integer> hostnamesVistos = new HashMap<>();
        Map<String, Integer> direccionesVistas = new HashMap<>();
        List<AnalisisImportacion.Fila> filas = new ArrayList<>(tabla.filas().size());
        for (FilaArchivo fila : tabla.filas()) {
            if (fila.valores().isEmpty() && fila.problemas().isEmpty()) {
                continue; // registro vacío, como una fila en blanco de la hoja
            }
            filas.add(clasificarFila(fila, presentes, referencias, hostnamesVistos, direccionesVistas));
        }
        if (filas.isEmpty()) {
            throw new ReglaNegocioException("El archivo no contiene registros con datos");
        }
        return new AnalisisImportacion(formato, List.copyOf(reconocidas), List.copyOf(ignoradas), filas);
    }

    private AnalisisImportacion.Fila clasificarFila(FilaArchivo fila, Set<ColumnaInventario> presentes, Referencias ref,
                                                    Map<String, Integer> hostnamesVistos,
                                                    Map<String, Integer> direccionesVistas) {
        List<String> errores = new ArrayList<>(fila.problemas());
        String hostname = fila.valor(HOSTNAME.clave());
        String ip = minusculas(fila.valor(DIRECCION_IP.clave()));

        for (ColumnaInventario columna : ColumnaInventario.importables()) {
            if (columna.obligatoria() && fila.valor(columna.clave()) == null) {
                errores.add("Falta el valor de «%s»".formatted(columna.etiqueta()));
            }
        }
        if (hostname != null) {
            if (hostname.length() > 255) {
                errores.add("El hostname no puede superar los 255 caracteres");
            } else if (!PATRON_HOSTNAME.matcher(hostname).matches()) {
                errores.add("El hostname solo admite letras, números, puntos, guiones y guiones bajos");
            }
        }
        if (ip != null) {
            if (ip.length() > 45) {
                errores.add("La dirección IP no puede superar los 45 caracteres");
            } else if (!ValidadorDireccionIp.esValida(ip)) {
                errores.add("La dirección IP %s no es válida".formatted(ip));
            }
        }
        exigirLongitud(fila, DATACENTER, 255, errores);
        exigirLongitud(fila, SERVIDOR_FISICO, 255, errores);
        exigirLongitud(fila, VLAN, 100, errores);
        exigirLongitud(fila, CLUSTER, 255, errores);
        exigirLongitud(fila, DNS, 255, errores);
        exigirLongitud(fila, PLATAFORMA, 255, errores);
        exigirLongitud(fila, DESCRIPCION, 500, errores);

        VersionSistemaOperativo version = ref.version(fila.valor(SISTEMA_OPERATIVO.clave()),
                fila.valor(VERSION.clave()), errores);
        Entorno entorno = ref.buscar(ref.entornos, fila.valor(ENTORNO.clave()), "el entorno", errores);
        NivelCriticidad criticidad = ref.buscar(ref.criticidades, fila.valor(CRITICIDAD.clave()),
                "el nivel de criticidad", errores);
        Usuario responsable = ref.buscar(ref.usuarios, fila.valor(RESPONSABLE.clave()), "el usuario", errores, false);

        if (!errores.isEmpty()) {
            return new AnalisisImportacion.Fila(fila.numero(), EstadoFila.ERRONEA, hostname, ip,
                    List.copyOf(errores), null, false, List.of(), null);
        }

        DatosServidor datos = new DatosServidor(hostname, ip, fila.valor(DATACENTER.clave()),
                fila.valor(SERVIDOR_FISICO.clave()), fila.valor(VLAN.clave()), fila.valor(CLUSTER.clave()),
                fila.valor(DNS.clave()), version.getId(), fila.valor(PLATAFORMA.clave()), entorno.getId(),
                criticidad.getId(), responsable.getId(), fila.valor(DESCRIPCION.clave()));

        // Repetido dentro del mismo archivo: solo cuenta la primera aparición.
        Integer filaHostname = hostnamesVistos.putIfAbsent(minusculas(hostname), fila.numero());
        Integer filaIp = direccionesVistas.putIfAbsent(ip, fila.numero());
        if (filaHostname != null || filaIp != null) {
            String motivo = filaHostname != null
                    ? "Repite el hostname de la fila %d del archivo".formatted(filaHostname)
                    : "Repite la dirección IP de la fila %d del archivo".formatted(filaIp);
            return new AnalisisImportacion.Fila(fila.numero(), EstadoFila.DUPLICADA, hostname, ip,
                    List.of(motivo), null, false, List.of(), datos);
        }

        Servidor porHostname = ref.porHostname.get(minusculas(hostname));
        Servidor porIp = ref.porIp.get(ip);
        if (porHostname == null && porIp == null) {
            List<String> inactivos = referenciasInactivas(null, version, entorno, criticidad, responsable);
            return inactivos.isEmpty()
                    ? new AnalisisImportacion.Fila(fila.numero(), EstadoFila.NUEVA, hostname, ip,
                            List.of(), null, false, List.of(), datos)
                    : new AnalisisImportacion.Fila(fila.numero(), EstadoFila.ERRONEA, hostname, ip,
                            inactivos, null, false, List.of(), null);
        }
        if (porHostname != null && porIp != null && !porHostname.getId().equals(porIp.getId())) {
            return new AnalisisImportacion.Fila(fila.numero(), EstadoFila.DUPLICADA, hostname, ip,
                    List.of("El hostname corresponde al servidor %s y la dirección IP al servidor %s"
                            .formatted(porHostname.getHostname(), porIp.getHostname())),
                    null, false, List.of(), datos);
        }
        Servidor existente = porHostname != null ? porHostname : porIp;
        return duplicadoDeRegistrado(fila.numero(), existente, porHostname != null, porIp != null,
                conservarAusentes(datos, existente, presentes), version, entorno, criticidad, responsable);
    }

    /**
     * Al sobrescribir, una columna opcional que no viene en el archivo conserva
     * el dato registrado; si viene vacía, lo borra. Así un archivo con solo
     * algunas columnas actualiza esas columnas y nada más (DEC-31).
     */
    private static DatosServidor conservarAusentes(DatosServidor d, Servidor s, Set<ColumnaInventario> presentes) {
        return new DatosServidor(d.hostname(), d.direccionIp(),
                presentes.contains(DATACENTER) ? d.datacenter() : s.getDatacenter(),
                presentes.contains(SERVIDOR_FISICO) ? d.servidorFisico() : s.getServidorFisico(),
                presentes.contains(VLAN) ? d.vlan() : s.getVlan(),
                presentes.contains(CLUSTER) ? d.cluster() : s.getCluster(),
                presentes.contains(DNS) ? d.dns() : s.getDns(),
                d.idVersionSistemaOperativo(),
                presentes.contains(PLATAFORMA) ? d.plataforma() : s.getPlataforma(),
                d.idEntorno(), d.idNivelCriticidad(), d.idResponsable(),
                presentes.contains(DESCRIPCION) ? d.descripcion() : s.getDescripcion());
    }

    /**
     * Coincide con un servidor registrado. Se puede sobrescribir si el usuario
     * lo confirma (HU08 CA4), siempre que la edición individual lo permitiría.
     */
    private AnalisisImportacion.Fila duplicadoDeRegistrado(int numero, Servidor existente, boolean mismoHostname,
                                                           boolean mismaIp, DatosServidor datos,
                                                           VersionSistemaOperativo version, Entorno entorno,
                                                           NivelCriticidad criticidad, Usuario responsable) {
        List<String> motivos = new ArrayList<>();
        motivos.add(mismoHostname && mismaIp
                ? "Ya existe el servidor %s con el mismo hostname y dirección IP".formatted(existente.getHostname())
                : mismoHostname
                ? "Ya existe el servidor %s con el mismo hostname".formatted(existente.getHostname())
                : "Ya existe el servidor %s con la misma dirección IP".formatted(existente.getHostname()));
        List<String> cambios = camposModificados(existente, datos, version);
        boolean sobrescribible = false;
        if (existente.getEstado() == EstadoServidor.DADO_DE_BAJA) {
            motivos.add("Está dado de baja: no se puede sobrescribir");
        } else if (cambios.isEmpty()) {
            motivos.add("Sus datos coinciden con los registrados");
        } else {
            List<String> bloqueos = referenciasInactivas(existente, version, entorno, criticidad, responsable);
            servicioServidor.conflictoConGrupos(existente, datos).ifPresent(bloqueos::add);
            motivos.addAll(bloqueos);
            sobrescribible = bloqueos.isEmpty();
        }
        return new AnalisisImportacion.Fila(numero, EstadoFila.DUPLICADA, datos.hostname(), datos.direccionIp(),
                List.copyOf(motivos), existente, sobrescribible, cambios, datos);
    }

    /**
     * Lo que se asigna debe estar activo; al sobrescribir, solo lo que cambia
     * (la misma regla que la edición individual).
     */
    private static List<String> referenciasInactivas(Servidor existente, VersionSistemaOperativo version,
                                                     Entorno entorno, NivelCriticidad criticidad,
                                                     Usuario responsable) {
        List<String> motivos = new ArrayList<>();
        if (cambia(existente, s -> s.getVersionSistemaOperativo().getId(), version.getId())
                && (!version.isActivo() || !version.getSistemaOperativo().isActivo())) {
            motivos.add("La versión de sistema operativo %s no está activa".formatted(version.descripcionCompleta()));
        }
        if (cambia(existente, s -> s.getEntorno().getId(), entorno.getId()) && !entorno.isActivo()) {
            motivos.add("El entorno %s no está activo".formatted(entorno.getNombre()));
        }
        if (cambia(existente, s -> s.getNivelCriticidad().getId(), criticidad.getId()) && !criticidad.isActivo()) {
            motivos.add("El nivel de criticidad %s no está activo".formatted(criticidad.getNombre()));
        }
        if (cambia(existente, s -> s.getResponsable().getId(), responsable.getId())) {
            ServicioServidor.motivoResponsableInvalido(responsable).ifPresent(motivos::add);
        }
        return motivos;
    }

    private static boolean cambia(Servidor existente, Function<Servidor, Integer> actual, Integer nuevo) {
        return existente == null || !Objects.equals(actual.apply(existente), nuevo);
    }

    /** Etiquetas de los datos del servidor registrado que el archivo reemplazaría. */
    private static List<String> camposModificados(Servidor s, DatosServidor d, VersionSistemaOperativo version) {
        List<String> cambios = new ArrayList<>();
        comparar(cambios, HOSTNAME, s.getHostname(), d.hostname());
        comparar(cambios, DIRECCION_IP, s.getDireccionIp(), d.direccionIp());
        comparar(cambios, DATACENTER, s.getDatacenter(), d.datacenter());
        comparar(cambios, SERVIDOR_FISICO, s.getServidorFisico(), d.servidorFisico());
        comparar(cambios, VLAN, s.getVlan(), d.vlan());
        comparar(cambios, CLUSTER, s.getCluster(), d.cluster());
        comparar(cambios, DNS, s.getDns(), d.dns());
        comparar(cambios, SISTEMA_OPERATIVO, s.getVersionSistemaOperativo().getSistemaOperativo().getId(),
                version.getSistemaOperativo().getId());
        comparar(cambios, VERSION, s.getVersionSistemaOperativo().getId(), d.idVersionSistemaOperativo());
        comparar(cambios, PLATAFORMA, s.getPlataforma(), d.plataforma());
        comparar(cambios, ENTORNO, s.getEntorno().getId(), d.idEntorno());
        comparar(cambios, CRITICIDAD, s.getNivelCriticidad().getId(), d.idNivelCriticidad());
        comparar(cambios, RESPONSABLE, s.getResponsable().getId(), d.idResponsable());
        comparar(cambios, DESCRIPCION, s.getDescripcion(), d.descripcion());
        return cambios;
    }

    private static void comparar(List<String> cambios, ColumnaInventario columna, Object actual, Object nuevo) {
        if (!Objects.equals(actual, nuevo)) {
            cambios.add(columna.etiqueta());
        }
    }

    private static void exigirLongitud(FilaArchivo fila, ColumnaInventario columna, int maximo, List<String> errores) {
        String valor = fila.valor(columna.clave());
        if (valor != null && valor.length() > maximo) {
            errores.add("«%s» no puede superar los %d caracteres".formatted(columna.etiqueta(), maximo));
        }
    }

    private static String minusculas(String valor) {
        return valor == null ? null : valor.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- confirmar

    /**
     * HU08 CA3 y CA4: registra los nuevos, sobrescribe solo los duplicados que
     * el usuario confirmó y reporta el resultado de cada fila.
     *
     * @param sobrescribir números de fila duplicados cuya sobrescritura se confirmó
     */
    public ResultadoImportacion importar(String nombreArchivo, byte[] contenido, Set<Integer> sobrescribir,
                                         Actor actor) {
        AnalisisImportacion analisis = analizar(nombreArchivo, contenido);
        Set<Integer> confirmadas = sobrescribir == null ? Set.of() : sobrescribir;
        TransactionTemplate porFila = new TransactionTemplate(transacciones);
        porFila.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<ResultadoImportacion.Fila> filas = new ArrayList<>(analisis.filas().size());
        for (AnalisisImportacion.Fila fila : analisis.filas()) {
            filas.add(switch (fila.estado()) {
                case ERRONEA -> resultado(fila, Resultado.RECHAZADO, null, String.join(". ", fila.motivos()));
                case NUEVA -> aplicar(porFila, fila, Resultado.CREADO,
                        () -> servicioServidor.crear(fila.datos(), actor).getId());
                case DUPLICADA -> fila.sobrescribible() && confirmadas.contains(fila.numero())
                        ? aplicar(porFila, fila, Resultado.ACTUALIZADO,
                                () -> servicioServidor.actualizar(fila.existente().getId(), fila.datos(), actor).getId())
                        : resultado(fila, Resultado.OMITIDO, fila.existente() == null ? null : fila.existente().getId(),
                                fila.sobrescribible()
                                        ? "No se confirmó sobrescribir el servidor existente"
                                        : String.join(". ", fila.motivos()));
            });
        }
        ResultadoImportacion resultado = new ResultadoImportacion(filas);

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("archivo", nombreArchivo);
        resumen.put("formato", analisis.formato().name());
        for (Resultado r : Resultado.values()) {
            resumen.put(r.name().toLowerCase(Locale.ROOT), resultado.contar(r));
        }
        porFila.executeWithoutResult(estado -> auditoria.registrar(actor,
                Operacion.de("IMPORTAR_INVENTARIO", "servidor", null).valores(null, resumen)));
        return resultado;
    }

    private ResultadoImportacion.Fila aplicar(TransactionTemplate porFila, AnalisisImportacion.Fila fila,
                                              Resultado exito, Supplier<Integer> operacion) {
        try {
            Integer id = porFila.execute(estado -> operacion.get());
            return resultado(fila, exito, id, null);
        } catch (ConflictoException | ReglaNegocioException | RecursoNoEncontradoException ex) {
            return resultado(fila, Resultado.RECHAZADO, null, ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            return resultado(fila, Resultado.RECHAZADO, null,
                    "El registro entra en conflicto con otro dato registrado");
        } catch (RuntimeException ex) {
            log.warn("Fila {} de la importación rechazada por un error inesperado", fila.numero(), ex);
            return resultado(fila, Resultado.RECHAZADO, null, "No se pudo registrar por un error inesperado");
        }
    }

    private static ResultadoImportacion.Fila resultado(AnalisisImportacion.Fila fila, Resultado resultado,
                                                       Integer idServidor, String detalle) {
        return new ResultadoImportacion.Fila(fila.numero(), fila.hostname(), resultado, idServidor, detalle);
    }

    // ------------------------------------------------------------- referencias

    /** Catálogos, usuarios y servidores coincidentes, cargados una vez por archivo. */
    private Referencias cargarReferencias(TablaArchivo tabla) {
        Set<String> hostnames = new HashSet<>(Set.of(""));
        Set<String> direcciones = new HashSet<>(Set.of(""));
        for (FilaArchivo fila : tabla.filas()) {
            String hostname = fila.valor(HOSTNAME.clave());
            String ip = fila.valor(DIRECCION_IP.clave());
            if (hostname != null) {
                hostnames.add(minusculas(hostname));
            }
            if (ip != null) {
                direcciones.add(minusculas(ip));
            }
        }
        Map<String, Servidor> porHostname = new HashMap<>();
        Map<String, Servidor> porIp = new HashMap<>();
        for (Servidor s : servidores.findCoincidentes(hostnames, direcciones)) {
            porHostname.put(minusculas(s.getHostname()), s);
            porIp.put(s.getDireccionIp(), s);
        }
        return new Referencias(
                indice(catalogos.listarSistemasOperativos(), SistemaOperativo::getNombre),
                indice(catalogos.listarEntornos(), Entorno::getNombre),
                indice(catalogos.listarCriticidades(), NivelCriticidad::getNombre),
                indice(usuarios.findAll(), Usuario::getCodigo),
                porHostname, porIp);
    }

    private static <T> Map<String, T> indice(Collection<T> elementos, Function<T, String> nombre) {
        Map<String, T> indice = new LinkedHashMap<>();
        elementos.forEach(e -> indice.putIfAbsent(clave(nombre.apply(e)), e));
        return indice;
    }

    private static String clave(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record Referencias(Map<String, SistemaOperativo> sistemas,
                               Map<String, Entorno> entornos,
                               Map<String, NivelCriticidad> criticidades,
                               Map<String, Usuario> usuarios,
                               Map<String, Servidor> porHostname,
                               Map<String, Servidor> porIp) {

        /**
         * Busca por nombre sin distinguir mayúsculas. Si no existe, el motivo
         * lista los valores registrados para que el usuario corrija el archivo.
         */
        <T> T buscar(Map<String, T> indice, String nombre, String descripcion, List<String> errores) {
            return buscar(indice, nombre, descripcion, errores, true);
        }

        /** @param listar si el motivo enumera los valores registrados (no se hace con usuarios) */
        <T> T buscar(Map<String, T> indice, String nombre, String descripcion, List<String> errores, boolean listar) {
            if (nombre == null) {
                return null;
            }
            T encontrado = indice.get(clave(nombre));
            if (encontrado == null) {
                errores.add("No existe %s «%s»%s".formatted(descripcion, nombre, listar ? disponibles(indice) : ""));
            }
            return encontrado;
        }

        VersionSistemaOperativo version(String sistema, String version, List<String> errores) {
            SistemaOperativo so = buscar(sistemas, sistema, "el sistema operativo", errores);
            if (so == null || version == null) {
                return null;
            }
            Map<String, VersionSistemaOperativo> versiones =
                    indice(so.getVersiones(), VersionSistemaOperativo::getVersion);
            VersionSistemaOperativo encontrada = versiones.get(clave(version));
            if (encontrada == null) {
                errores.add("El sistema operativo %s no tiene registrada la versión «%s»%s"
                        .formatted(so.getNombre(), version, disponibles(versiones)));
            }
            return encontrada;
        }

        private static String disponibles(Map<String, ?> indice) {
            if (indice.isEmpty() || indice.size() > 15) {
                return "";
            }
            return ". Registrados: " + indice.values().stream()
                    .map(Referencias::nombreDe)
                    .collect(Collectors.joining(", "));
        }

        private static String nombreDe(Object elemento) {
            return switch (elemento) {
                case SistemaOperativo so -> so.getNombre();
                case VersionSistemaOperativo v -> v.getVersion();
                case Entorno e -> e.getNombre();
                case NivelCriticidad n -> n.getNombre();
                default -> String.valueOf(elemento);
            };
        }
    }
}
