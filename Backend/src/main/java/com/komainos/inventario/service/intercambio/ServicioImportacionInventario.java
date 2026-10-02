package com.komainos.inventario.service.intercambio;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.SistemaOperativo;
import com.komainos.inventario.model.VersionSistemaOperativo;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.inventario.service.PuertoCredencialesInventario;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.inventario.service.intercambio.AnalisisImportacion.EstadoFila;
import com.komainos.inventario.service.intercambio.ResultadoImportacion.Resultado;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.archivo.ArchivoGenerado;
import com.komainos.shared.util.archivo.ColumnaArchivo;
import com.komainos.shared.util.archivo.EscritorTabular;
import com.komainos.shared.util.archivo.FilaArchivo;
import com.komainos.shared.util.archivo.FormatoArchivo;
import com.komainos.shared.util.archivo.LectorTabular;
import com.komainos.shared.util.archivo.TablaArchivo;
import com.komainos.shared.validation.ValidadorDireccionIp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.math.BigDecimal;
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

import static com.komainos.inventario.service.intercambio.ColumnaInventario.CLUSTER;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.CPU;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.DISCO_VIRTUAL_GB;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.IPS_ADICIONALES;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.RAM_GB;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.VDC;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.CRITICIDAD;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.DESCRIPCION;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.DIRECCION_IP;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.DNS;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.ENTORNO;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.HOSTNAME;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.PLATAFORMA;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.RESPONSABLE;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.SERVIDOR_FISICO;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.SISTEMA_OPERATIVO;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.VERSION;
import static com.komainos.inventario.service.intercambio.ColumnaInventario.VLAN;

/** Analizar no escribe nada, importar vuelve a analizar con los datos del momento y registra cada fila en su propia transacción */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioImportacionInventario {

    private static final Pattern SEPARADOR_DIRECCIONES = Pattern.compile("[;,\\s]+");

    /** La misma regla que el alta individual (ServidorPeticion) */
    private static final Pattern PATRON_HOSTNAME = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$");

    private final LectorTabular lector;
    private final EscritorTabular escritor;
    private final ServidorRepositorio servidores;
    private final UsuarioRepositorio usuarios;
    private final ServicioCatalogos catalogos;
    private final ServicioServidor servicioServidor;
    private final PuertoCredencialesInventario credenciales;
    private final ServicioAuditoria auditoria;
    private final PlatformTransactionManager transacciones;

    /** Las columnas de la credencial principal van al final y son opcionales (DEC-39) */
    public ArchivoGenerado plantilla(FormatoArchivo formato) {
        List<ColumnaArchivo> columnas = new ArrayList<>(ColumnaInventario.importables().stream()
                .map(ColumnaInventario::comoColumnaArchivo).toList());
        Arrays.stream(ColumnaCredencial.values()).map(ColumnaCredencial::comoColumnaArchivo).forEach(columnas::add);
        Map<String, Object> vacio = new HashMap<>();
        byte[] contenido = escritor.escribir(formato, columnas, List.of(vacio), "Servidores");
        return new ArchivoGenerado("plantilla_inventario_servidores." + formato.extension(), formato, contenido);
    }

    public AnalisisImportacion analizar(String nombreArchivo, byte[] contenido) {
        FormatoArchivo formato = FormatoArchivo.deNombreArchivo(nombreArchivo);
        TablaArchivo tabla = lector.leer(formato, contenido);
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        return lectura.execute(estado -> clasificar(formato, tabla));
    }

    private AnalisisImportacion clasificar(FormatoArchivo formato, TablaArchivo tabla) {
        List<ColumnaInventario> reconocidas = new ArrayList<>();
        List<ColumnaCredencial> deCredencial = new ArrayList<>();
        List<String> ignoradas = new ArrayList<>();
        for (String clave : tabla.columnas()) {
            ColumnaInventario columna = ColumnaInventario.deClave(clave);
            ColumnaCredencial credencial = ColumnaCredencial.deClave(clave);
            if (columna != null && columna.importable()) {
                reconocidas.add(columna);
            } else if (credencial != null) {
                deCredencial.add(credencial);
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
            filas.add(clasificarFila(fila, presentes, !deCredencial.isEmpty(), referencias, hostnamesVistos,
                    direccionesVistas));
        }
        if (filas.isEmpty()) {
            throw new ReglaNegocioException("El archivo no contiene registros con datos");
        }
        return new AnalisisImportacion(formato, List.copyOf(reconocidas), List.copyOf(deCredencial),
                List.copyOf(ignoradas), filas);
    }

    private AnalisisImportacion.Fila clasificarFila(FilaArchivo fila, Set<ColumnaInventario> presentes,
                                                    boolean conCredencial, Referencias ref,
                                                    Map<String, Integer> hostnamesVistos,
                                                    Map<String, Integer> direccionesVistas) {
        List<String> errores = new ArrayList<>(fila.problemas());
        String hostname = fila.valor(HOSTNAME.clave());
        String ip = minusculas(fila.valor(DIRECCION_IP.clave()));
        List<String> adicionales = separarDirecciones(fila.valor(IPS_ADICIONALES.clave()));

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
            validarIp(ip, "La dirección IP", errores);
        }
        adicionales.forEach(adicional -> validarIp(adicional, "La dirección IP adicional", errores));
        if (adicionales.size() > 20) {
            errores.add("Un servidor puede tener hasta 20 direcciones IP adicionales");
        }
        List<String> todas = new ArrayList<>();
        if (ip != null) {
            todas.add(ip);
        }
        todas.addAll(adicionales);
        Set<String> unicas = new HashSet<>();
        todas.stream().filter(d -> !unicas.add(d)).distinct()
                .forEach(d -> errores.add("La dirección IP %s está repetida en el registro".formatted(d)));
        exigirLongitud(fila, VDC, 255, errores);
        exigirLongitud(fila, SERVIDOR_FISICO, 255, errores);
        exigirLongitud(fila, VLAN, 100, errores);
        exigirLongitud(fila, CLUSTER, 255, errores);
        exigirLongitud(fila, DNS, 255, errores);
        exigirLongitud(fila, PLATAFORMA, 255, errores);
        exigirLongitud(fila, DESCRIPCION, 500, errores);
        Integer cpu = entero(fila, CPU, 4096, errores);
        BigDecimal ram = decimal(fila, RAM_GB, 5, errores);
        BigDecimal disco = decimal(fila, DISCO_VIRTUAL_GB, 8, errores);

        VersionSistemaOperativo version = ref.version(fila.valor(SISTEMA_OPERATIVO.clave()),
                fila.valor(VERSION.clave()), errores);
        Entorno entorno = ref.buscar(ref.entornos, fila.valor(ENTORNO.clave()), "el entorno", errores);
        NivelCriticidad criticidad = ref.buscar(ref.criticidades, fila.valor(CRITICIDAD.clave()),
                "el nivel de criticidad", errores);
        Usuario responsable = ref.buscar(ref.usuarios, fila.valor(RESPONSABLE.clave()), "el usuario", errores, false);
        CredencialArchivo credencial = conCredencial ? ColumnaCredencial.leer(fila) : null;
        if (credencial != null && version != null) {
            errores.addAll(credenciales.validar(credencial, version.getSistemaOperativo().getFamilia()));
        }

        if (!errores.isEmpty()) {
            return new AnalisisImportacion.Fila(fila.numero(), EstadoFila.ERRONEA, hostname, ip,
                    List.copyOf(errores), null, false, List.of(), null, null);
        }

        DatosServidor datos = new DatosServidor(hostname, ip, fila.valor(VDC.clave()),
                fila.valor(SERVIDOR_FISICO.clave()), fila.valor(VLAN.clave()), fila.valor(CLUSTER.clave()),
                fila.valor(DNS.clave()), version.getId(), fila.valor(PLATAFORMA.clave()), entorno.getId(),
                criticidad.getId(), responsable.getId(), fila.valor(DESCRIPCION.clave()),
                adicionales, cpu, ram, disco);

        // Repetido en el archivo por hostname o por cualquiera de sus IP: solo cuenta la primera aparición
        Integer filaHostname = hostnamesVistos.get(minusculas(hostname));
        String ipRepetida = todas.stream().filter(direccionesVistas::containsKey).findFirst().orElse(null);
        if (filaHostname != null || ipRepetida != null) {
            String motivo = filaHostname != null
                    ? "Repite el hostname de la fila %d del archivo".formatted(filaHostname)
                    : "Repite la dirección IP %s de la fila %d del archivo"
                            .formatted(ipRepetida, direccionesVistas.get(ipRepetida));
            return new AnalisisImportacion.Fila(fila.numero(), EstadoFila.DUPLICADA, hostname, ip,
                    List.of(motivo), null, false, List.of(), datos, null);
        }
        hostnamesVistos.put(minusculas(hostname), fila.numero());
        todas.forEach(d -> direccionesVistas.put(d, fila.numero()));

        Servidor porHostname = ref.porHostname.get(minusculas(hostname));
        Set<Servidor> porIp = new LinkedHashSet<>();
        todas.stream().map(ref.porIp::get).filter(Objects::nonNull).forEach(porIp::add);
        Set<Servidor> coincidentes = new LinkedHashSet<>();
        if (porHostname != null) {
            coincidentes.add(porHostname);
        }
        coincidentes.addAll(porIp);
        if (coincidentes.isEmpty()) {
            List<String> inactivos = referenciasInactivas(null, version, entorno, criticidad, responsable);
            return inactivos.isEmpty()
                    ? new AnalisisImportacion.Fila(fila.numero(), EstadoFila.NUEVA, hostname, ip,
                            List.of(), null, false, List.of(), datos, credencial)
                    : new AnalisisImportacion.Fila(fila.numero(), EstadoFila.ERRONEA, hostname, ip,
                            inactivos, null, false, List.of(), null, null);
        }
        if (coincidentes.size() > 1) {
            return new AnalisisImportacion.Fila(fila.numero(), EstadoFila.DUPLICADA, hostname, ip,
                    List.of("El hostname y las direcciones IP del registro pertenecen a servidores distintos: %s"
                            .formatted(coincidentes.stream().map(Servidor::getHostname).collect(Collectors.joining(", ")))),
                    null, false, List.of(), datos, null);
        }
        Servidor existente = coincidentes.iterator().next();
        return duplicadoDeRegistrado(fila.numero(), existente, porHostname != null, !porIp.isEmpty(),
                conservarAusentes(datos, existente, presentes), version, entorno, criticidad, responsable, credencial);
    }

    /** Columna ausente conserva el dato registrado, columna vacía lo borra (DEC-31) */
    private static DatosServidor conservarAusentes(DatosServidor d, Servidor s, Set<ColumnaInventario> presentes) {
        return new DatosServidor(d.hostname(), d.direccionIp(),
                presentes.contains(VDC) ? d.vdc() : s.getVdc(),
                presentes.contains(SERVIDOR_FISICO) ? d.servidorFisico() : s.getServidorFisico(),
                presentes.contains(VLAN) ? d.vlan() : s.getVlan(),
                presentes.contains(CLUSTER) ? d.cluster() : s.getCluster(),
                presentes.contains(DNS) ? d.dns() : s.getDns(),
                d.idVersionSistemaOperativo(),
                presentes.contains(PLATAFORMA) ? d.plataforma() : s.getPlataforma(),
                d.idEntorno(), d.idNivelCriticidad(), d.idResponsable(),
                presentes.contains(DESCRIPCION) ? d.descripcion() : s.getDescripcion(),
                // Sin la columna se conservan las IP adicionales, salvo la que pasa a ser principal
                presentes.contains(IPS_ADICIONALES) ? d.direccionesIpAdicionales()
                        : s.direccionesAdicionales().stream().filter(ip -> !ip.equals(d.direccionIp())).toList(),
                presentes.contains(CPU) ? d.cantidadCpu() : s.getCantidadCpu(),
                presentes.contains(RAM_GB) ? d.ramGb() : s.getRamGb(),
                presentes.contains(DISCO_VIRTUAL_GB) ? d.hdVirtualGb() : s.getHdVirtualGb());
    }

    /** Se puede sobrescribir solo si la edición individual lo permitiría (HU08 CA4) */
    private AnalisisImportacion.Fila duplicadoDeRegistrado(int numero, Servidor existente, boolean mismoHostname,
                                                           boolean mismaIp, DatosServidor datos,
                                                           VersionSistemaOperativo version, Entorno entorno,
                                                           NivelCriticidad criticidad, Usuario responsable,
                                                           CredencialArchivo credencial) {
        List<String> motivos = new ArrayList<>();
        motivos.add(mismoHostname && mismaIp
                ? "Ya existe el servidor %s con el mismo hostname y dirección IP".formatted(existente.getHostname())
                : mismoHostname
                ? "Ya existe el servidor %s con el mismo hostname".formatted(existente.getHostname())
                : "Ya existe el servidor %s con la misma dirección IP".formatted(existente.getHostname()));
        List<String> cambios = camposModificados(existente, datos, version);
        FamiliaSistemaOperativo familia = version.getSistemaOperativo().getFamilia();
        if (credencial != null && existente.getEstado() != EstadoServidor.DADO_DE_BAJA
                && credenciales.cambiaPrincipal(existente.getId(), familia, credencial)) {
            cambios.add(ColumnaCredencial.CAMBIO);
        }
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
                List.copyOf(motivos), existente, sobrescribible, cambios, datos, credencial);
    }

    /** Al sobrescribir solo se exige activo lo que cambia, como en la edición individual */
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

    private static List<String> camposModificados(Servidor s, DatosServidor d, VersionSistemaOperativo version) {
        List<String> cambios = new ArrayList<>();
        comparar(cambios, HOSTNAME, s.getHostname(), d.hostname());
        comparar(cambios, DIRECCION_IP, s.getDireccionIp(), d.direccionIp());
        comparar(cambios, IPS_ADICIONALES, new TreeSet<>(s.direccionesAdicionales()),
                new TreeSet<>(d.direccionesIpAdicionales()));
        comparar(cambios, VDC, s.getVdc(), d.vdc());
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
        comparar(cambios, CPU, s.getCantidadCpu(), d.cantidadCpu());
        compararNumero(cambios, RAM_GB, s.getRamGb(), d.ramGb());
        compararNumero(cambios, DISCO_VIRTUAL_GB, s.getHdVirtualGb(), d.hdVirtualGb());
        return cambios;
    }

    private static void comparar(List<String> cambios, ColumnaInventario columna, Object actual, Object nuevo) {
        if (!Objects.equals(actual, nuevo)) {
            cambios.add(columna.etiqueta());
        }
    }

    /** 16 y 16.00 son el mismo valor: BigDecimal.equals distingue la escala */
    private static void compararNumero(List<String> cambios, ColumnaInventario columna, BigDecimal actual,
                                       BigDecimal nuevo) {
        boolean iguales = actual == null ? nuevo == null : nuevo != null && actual.compareTo(nuevo) == 0;
        if (!iguales) {
            cambios.add(columna.etiqueta());
        }
    }

    private static List<String> separarDirecciones(String valor) {
        if (valor == null) {
            return List.of();
        }
        return Arrays.stream(SEPARADOR_DIRECCIONES.split(valor))
                .map(String::trim)
                .filter(texto -> !texto.isEmpty())
                .map(texto -> texto.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static void validarIp(String ip, String descripcion, List<String> errores) {
        if (ip.length() > 45) {
            errores.add("%s %s no puede superar los 45 caracteres".formatted(descripcion, ip));
        } else if (!ValidadorDireccionIp.esValida(ip)) {
            errores.add("%s %s no es válida".formatted(descripcion, ip));
        }
    }

    private static Integer entero(FilaArchivo fila, ColumnaInventario columna, int maximo, List<String> errores) {
        String valor = fila.valor(columna.clave());
        if (valor == null) {
            return null;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero <= 0 || numero > maximo) {
                errores.add("«%s» debe ser un número entero entre 1 y %d".formatted(columna.etiqueta(), maximo));
            }
            return numero;
        } catch (NumberFormatException ex) {
            errores.add("«%s» debe ser un número entero".formatted(columna.etiqueta()));
            return null;
        }
    }

    /** Acepta coma o punto decimal: una hoja en español suele escribir 16,5 */
    private static BigDecimal decimal(FilaArchivo fila, ColumnaInventario columna, int enteros, List<String> errores) {
        String valor = fila.valor(columna.clave());
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.contains(",") && !texto.contains(".")) {
            texto = texto.replace(',', '.');
        }
        try {
            BigDecimal numero = new BigDecimal(texto);
            BigDecimal sinCeros = numero.stripTrailingZeros();
            if (numero.signum() <= 0 || sinCeros.scale() > 2 || sinCeros.precision() - sinCeros.scale() > enteros) {
                errores.add("«%s» debe ser un número mayor que 0, con hasta %d enteros y 2 decimales"
                        .formatted(columna.etiqueta(), enteros));
            }
            return numero;
        } catch (NumberFormatException ex) {
            errores.add("«%s» debe ser un número".formatted(columna.etiqueta()));
            return null;
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
                case NUEVA -> aplicar(porFila, fila, Resultado.CREADO, () -> {
                    Servidor creado = servicioServidor.crear(fila.datos(), actor);
                    aplicarCredencial(fila, creado.getId(), actor);
                    return creado.getId();
                });
                case DUPLICADA -> fila.sobrescribible() && confirmadas.contains(fila.numero())
                        ? aplicar(porFila, fila, Resultado.ACTUALIZADO, () -> {
                            Integer id = fila.existente().getId();
                            if (fila.cambiaServidor()) {
                                servicioServidor.actualizar(id, fila.datos(), actor);
                            }
                            if (fila.cambiaCredencial()) {
                                aplicarCredencial(fila, id, actor);
                            }
                            return id;
                        })
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

    /** En la misma transacción que el servidor: si la credencial falla, la fila completa se rechaza */
    private void aplicarCredencial(AnalisisImportacion.Fila fila, Integer idServidor, Actor actor) {
        if (fila.credencial() != null) {
            credenciales.aplicarPrincipal(idServidor, servicioServidor.obtenerSinAlcance(idServidor).familia(),
                    fila.credencial(), actor);
        }
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
            direcciones.addAll(separarDirecciones(fila.valor(IPS_ADICIONALES.clave())));
        }
        Map<String, Servidor> porHostname = new HashMap<>();
        Map<String, Servidor> porIp = new HashMap<>();
        for (Servidor s : servidores.findCoincidentes(hostnames, direcciones)) {
            porHostname.put(minusculas(s.getHostname()), s);
            s.getDirecciones().forEach(d -> porIp.put(d.getDireccion(), s));
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

        <T> T buscar(Map<String, T> indice, String nombre, String descripcion, List<String> errores) {
            return buscar(indice, nombre, descripcion, errores, true);
        }

        /** Con usuarios no se enumeran los códigos registrados */
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
