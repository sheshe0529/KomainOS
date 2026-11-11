package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.EjecutorRemoto;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.FinalidadCuenta;
import com.komainos.inventario.service.ServicioAsignacionCuentas;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Paralelo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Verificación de conectividad, autenticación y permisos de una cuenta de servicio (RF71, HU03 CA3, HU04 CA4). Sin
 * cuenta indicada se prueba la que usaría la finalidad: la del servidor o grupo, o la predeterminada. Con una cuenta
 * indicada se prueba antes de asignarla; en un grupo se comprueba cada integrante (CP021). Los errores distinguen
 * comunicación, autenticación y permisos, que desde el DEC-57 significa que la elevación configurada falló (CP022,
 * CP023). Con acceso correcto informa si los scripts se ejecutarán con privilegios de administrador, sin exigirlo.
 */
@Service
@Slf4j
public class ServicioConectividad {

    private final ServicioServidor servidores;
    private final GrupoMantenimientoRepositorio grupos;
    private final ConfiguracionServidorRepositorio configuracionesServidor;
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final ServicioParametrosSistema parametros;
    private final PuertoCuentasServicio cuentas;
    private final EjecutorRemoto remoto;
    private final ServicioAuditoria auditoria;
    private final TransactionTemplate transaccion;

    public ServicioConectividad(ServicioServidor servidores, GrupoMantenimientoRepositorio grupos,
                                ConfiguracionServidorRepositorio configuracionesServidor,
                                ConfiguracionGrupoRepositorio configuracionesGrupo, ServicioParametrosSistema parametros,
                                PuertoCuentasServicio cuentas, EjecutorRemoto remoto, ServicioAuditoria auditoria,
                                PlatformTransactionManager transacciones) {
        this.servidores = servidores;
        this.grupos = grupos;
        this.configuracionesServidor = configuracionesServidor;
        this.configuracionesGrupo = configuracionesGrupo;
        this.parametros = parametros;
        this.cuentas = cuentas;
        this.remoto = remoto;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    public ResultadoConexion probarServidor(Integer idServidor, Integer idCuentaCandidata, FinalidadCuenta finalidad, Actor actor) {
        Prueba prueba = transaccion.execute(estado -> {
            Servidor s = servidores.obtenerSinAlcance(idServidor);
            Integer cuenta = idCuentaCandidata != null ? idCuentaCandidata : cuentaDelServidor(idServidor, finalidad);
            return preparar(s, cuenta, List.of(finalidad));
        });
        ResultadoConexion resultado = ejecutar(prueba);
        auditar(actor, "servidor " + prueba.hostname() + " · " + finalidad, List.of(resultado), idServidor);
        return resultado;
    }

    public List<ResultadoConexion> probarGrupo(Integer idGrupo, Integer idCuentaCandidata, FinalidadCuenta finalidad, Actor actor) {
        List<Prueba> pruebas = transaccion.execute(estado -> {
            List<Servidor> integrantes = integrantes(idGrupo);
            Integer cuenta = idCuentaCandidata != null ? idCuentaCandidata : cuentaDelGrupo(idGrupo, finalidad);
            return integrantes.stream().map(s -> preparar(s, cuenta, List.of(finalidad))).toList();
        });
        List<ResultadoConexion> resultados = ejecutarTodas(pruebas, "grupo " + idGrupo);
        auditar(actor, "grupo " + idGrupo + " · " + finalidad, resultados, null);
        return resultados;
    }

    /**
     * Un solo paso para el acceso y los permisos (DEC-59): prueba la cuenta de la evaluación y la del mantenimiento del
     * servidor, una sola vez si son la misma. Con una cuenta candidata, solo esa. Cada resultado dice para qué se usa
     * la cuenta probada
     */
    public List<ResultadoConexion> probarAccesoServidor(Integer idServidor, Integer idCuentaCandidata, Actor actor) {
        List<Prueba> pruebas = transaccion.execute(estado -> {
            Servidor s = servidores.obtenerSinAlcance(idServidor);
            return porCuenta(idCuentaCandidata, f -> cuentaDelServidor(idServidor, f)).entrySet().stream()
                    .map(c -> preparar(s, c.getKey(), c.getValue()))
                    .toList();
        });
        List<ResultadoConexion> resultados = ejecutarTodas(pruebas, "servidor " + idServidor);
        auditar(actor, "servidor " + pruebas.getFirst().hostname() + " · acceso", resultados, idServidor);
        return resultados;
    }

    /** Lo mismo en cada integrante del grupo (CP021) */
    public List<ResultadoConexion> probarAccesoGrupo(Integer idGrupo, Integer idCuentaCandidata, Actor actor) {
        List<Prueba> pruebas = transaccion.execute(estado -> {
            List<Servidor> integrantes = integrantes(idGrupo);
            Map<Integer, List<FinalidadCuenta>> cuentasAProbar = porCuenta(idCuentaCandidata,
                    f -> cuentaDelGrupo(idGrupo, f));
            return integrantes.stream()
                    .flatMap(s -> cuentasAProbar.entrySet().stream().map(c -> preparar(s, c.getKey(), c.getValue())))
                    .toList();
        });
        List<ResultadoConexion> resultados = ejecutarTodas(pruebas, "grupo " + idGrupo);
        auditar(actor, "grupo " + idGrupo + " · acceso", resultados, null);
        return resultados;
    }

    /** Sin cuenta candidata: la de cada finalidad, agrupadas para no probar dos veces la misma */
    private static Map<Integer, List<FinalidadCuenta>> porCuenta(Integer candidata,
                                                                 Function<FinalidadCuenta, Integer> cuentaDe) {
        Map<Integer, List<FinalidadCuenta>> cuentasAProbar = new LinkedHashMap<>();
        if (candidata != null) {
            cuentasAProbar.put(candidata, List.of());
            return cuentasAProbar;
        }
        for (FinalidadCuenta finalidad : FinalidadCuenta.values()) {
            Integer cuenta = cuentaDe.apply(finalidad);
            if (cuenta == null) {
                throw new ReglaNegocioException("Asigne una cuenta de servicio de %s o defina la predeterminada para "
                        .formatted(finalidad == FinalidadCuenta.EVALUACION ? "evaluación" : "mantenimiento")
                        + "probar el acceso");
            }
            cuentasAProbar.computeIfAbsent(cuenta, c -> new ArrayList<>()).add(finalidad);
        }
        return cuentasAProbar;
    }

    private Integer cuentaDelServidor(Integer idServidor, FinalidadCuenta finalidad) {
        return configuracionesServidor.findByServidorId(idServidor)
                .map(c -> ServicioAsignacionCuentas.propia(c, finalidad))
                .orElse(ServicioAsignacionCuentas.predeterminadaId(parametros.obtener(), finalidad));
    }

    private Integer cuentaDelGrupo(Integer idGrupo, FinalidadCuenta finalidad) {
        return configuracionesGrupo.findByGrupoId(idGrupo)
                .map(c -> ServicioAsignacionCuentas.propia(c, finalidad))
                .orElse(ServicioAsignacionCuentas.predeterminadaId(parametros.obtener(), finalidad));
    }

    private List<Servidor> integrantes(Integer idGrupo) {
        GrupoMantenimiento grupo = grupos.findConIntegrantesById(idGrupo)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el grupo", idGrupo));
        if (grupo.servidores().isEmpty()) {
            throw new ReglaNegocioException("El grupo %s no tiene integrantes".formatted(grupo.getNombre()));
        }
        return grupo.servidores();
    }

    /** En paralelo, dentro del límite de conexiones; ordenadas por servidor y, en cada uno, evaluación primero */
    private List<ResultadoConexion> ejecutarTodas(List<Prueba> pruebas, String objetivo) {
        if (pruebas.size() == 1) {
            return List.of(ejecutar(pruebas.getFirst()));
        }
        List<ResultadoConexion> resultados = Collections.synchronizedList(new ArrayList<>());
        Paralelo.paraCada(pruebas, p -> resultados.add(ejecutar(p)))
                .forEach(ex -> log.error("Error inesperado al probar la conectividad del {}", objetivo, ex));
        return resultados.stream()
                .sorted(Comparator.comparing(ResultadoConexion::servidor)
                        .thenComparing(r -> r.finalidades().isEmpty() ? 0 : r.finalidades().getFirst().ordinal()))
                .toList();
    }

    /** La cuenta debe existir, estar vigente y servir para la familia del servidor (una llave SSH no sirve en WinRM) */
    private Prueba preparar(Servidor s, Integer cuenta, List<FinalidadCuenta> finalidades) {
        if (cuenta == null) {
            throw new ReglaNegocioException("Asigne una cuenta de servicio o defina la predeterminada para probar el acceso");
        }
        String nombre = cuentas.nombre(cuenta).orElseThrow(() -> RecursoNoEncontradoException.de("la cuenta de servicio", cuenta));
        String problema = null;
        try {
            cuentas.exigirAsignable(cuenta, s.familia());
        } catch (ReglaNegocioException ex) {
            problema = ex.getMessage();
        }
        // El mismo script en las dos finalidades (DEC-57): el propósito solo distingue la evaluación en el registro
        SolicitudEjecucion solicitud = finalidades.equals(List.of(FinalidadCuenta.EVALUACION))
                ? SolicitudEjecucion.conectividadEvaluacion(s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(), cuenta, 2)
                : SolicitudEjecucion.conectividad(s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(), cuenta, 2);
        return new Prueba(s.getId(), s.getHostname(), cuenta, nombre, problema, solicitud, List.copyOf(finalidades));
    }

    private ResultadoConexion ejecutar(Prueba p) {
        if (p.problema() != null) {
            return new ResultadoConexion(p.idServidor(), p.hostname(), p.idCuenta(), p.cuenta(), false,
                    TipoFallo.CONFIGURACION, p.problema(), null, p.finalidades());
        }
        ResultadoEjecucion r = remoto.probarConexion(p.solicitud());
        if (!r.correcto()) {
            return new ResultadoConexion(p.idServidor(), p.hostname(), p.idCuenta(), p.cuenta(), false, r.fallo(),
                    r.error(), null, p.finalidades());
        }
        Permisos permisos = permisos(r.salida());
        return new ResultadoConexion(p.idServidor(), p.hostname(), p.idCuenta(), p.cuenta(), true, null,
                permisos.mensaje(), permisos.administrador(), p.finalidades());
    }

    /**
     * El script de la prueba termina con KOMAINOS_ADMINISTRADOR=si|no (DEC-57): los permisos efectivos se informan, no se
     * exigen. Sin la marca, el resultado no dice nada sobre ellos
     */
    static Permisos permisos(String salida) {
        Boolean administrador = null;
        List<String> lineas = new ArrayList<>();
        for (String linea : salida.strip().lines().toList()) {
            String l = linea.strip();
            if (l.startsWith(MARCA_ADMINISTRADOR)) {
                administrador = l.substring(MARCA_ADMINISTRADOR.length()).trim().equalsIgnoreCase("si");
            } else if (!l.isEmpty()) {
                lineas.add(l);
            }
        }
        return new Permisos(String.join("\n", lineas), administrador);
    }

    private static final String MARCA_ADMINISTRADOR = "KOMAINOS_ADMINISTRADOR=";

    record Permisos(String mensaje, Boolean administrador) {
    }

    private void auditar(Actor actor, String objetivo, List<ResultadoConexion> resultados, Integer idServidor) {
        long fallidos = resultados.stream().filter(r -> !r.correcto()).count();
        transaccion.executeWithoutResult(estado -> auditoria.registrar(actor,
                Operacion.de("PROBAR_CONECTIVIDAD", "cuenta_servicio", resultados.getFirst().idCuenta())
                        .sobreServidor(idServidor)
                        .motivo("%s con %s: %s".formatted(objetivo, resultados.getFirst().cuenta(),
                                fallidos == 0 ? "acceso verificado" : fallidos + " con error"))));
    }

    private record Prueba(Integer idServidor, String hostname, Integer idCuenta, String cuenta, String problema,
                          SolicitudEjecucion solicitud, List<FinalidadCuenta> finalidades) {
    }

    /**
     * @param fallo         nulo si el acceso se verificó; si no, comunicación, autenticación, permisos (la elevación
     *                      falló) o configuración
     * @param administrador si los scripts se ejecutarán con privilegios de administrador; nulo si no se llegó a saber
     * @param finalidades   para qué usa el servidor la cuenta probada; vacía si es una candidata
     */
    public record ResultadoConexion(Integer idServidor, String servidor, Integer idCuenta, String cuenta,
                                    boolean correcto, TipoFallo fallo, String mensaje, Boolean administrador,
                                    List<FinalidadCuenta> finalidades) {
    }
}
