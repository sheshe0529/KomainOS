package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.EjecutorRemoto;
import com.komainos.inventario.model.GrupoMantenimiento;
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
import java.util.List;

/**
 * Verificación de conectividad, autenticación y permisos de una cuenta de servicio (RF71, HU03 CA3, HU04 CA4). Sin
 * cuenta indicada se prueba la que usaría el mantenimiento: la del servidor o grupo, o la predeterminada. Con una cuenta
 * indicada se prueba antes de asignarla; en un grupo se comprueba cada integrante (CP021). Los errores distinguen
 * comunicación, autenticación y permisos (CP022, CP023).
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

    public ResultadoConexion probarServidor(Integer idServidor, Integer idCuentaCandidata, Actor actor) {
        Prueba prueba = transaccion.execute(estado -> {
            Servidor s = servidores.obtenerSinAlcance(idServidor);
            Integer cuenta = idCuentaCandidata != null ? idCuentaCandidata : configuracionesServidor.findByServidorId(idServidor)
                    .map(c -> c.getIdCuentaServicio()).orElse(parametros.obtener().getIdCuentaServicioPredeterminada());
            return preparar(s, cuenta);
        });
        ResultadoConexion resultado = ejecutar(prueba);
        auditar(actor, "servidor " + prueba.hostname(), List.of(resultado), idServidor);
        return resultado;
    }

    public List<ResultadoConexion> probarGrupo(Integer idGrupo, Integer idCuentaCandidata, Actor actor) {
        List<Prueba> pruebas = transaccion.execute(estado -> {
            GrupoMantenimiento grupo = grupos.findConIntegrantesById(idGrupo)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el grupo", idGrupo));
            if (grupo.servidores().isEmpty()) {
                throw new ReglaNegocioException("El grupo %s no tiene integrantes".formatted(grupo.getNombre()));
            }
            Integer cuenta = idCuentaCandidata != null ? idCuentaCandidata : configuracionesGrupo.findByGrupoId(idGrupo)
                    .map(c -> c.getIdCuentaServicio()).orElse(parametros.obtener().getIdCuentaServicioPredeterminada());
            return grupo.servidores().stream().map(s -> preparar(s, cuenta)).toList();
        });
        List<ResultadoConexion> resultados = Collections.synchronizedList(new ArrayList<>());
        Paralelo.paraCada(pruebas, p -> resultados.add(ejecutar(p)))
                .forEach(ex -> log.error("Error inesperado al probar la conectividad del grupo {}", idGrupo, ex));
        List<ResultadoConexion> ordenados = resultados.stream()
                .sorted(Comparator.comparing(ResultadoConexion::servidor))
                .toList();
        auditar(actor, "grupo " + idGrupo, ordenados, null);
        return ordenados;
    }

    /** La cuenta debe existir, estar vigente y servir para la familia del servidor (una llave SSH no sirve en WinRM) */
    private Prueba preparar(Servidor s, Integer cuenta) {
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
        return new Prueba(s.getId(), s.getHostname(), cuenta, nombre, problema, SolicitudEjecucion.conectividad(
                s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(), cuenta, 2));
    }

    private ResultadoConexion ejecutar(Prueba p) {
        if (p.problema() != null) {
            return new ResultadoConexion(p.idServidor(), p.hostname(), p.idCuenta(), p.cuenta(), remoto.simulado(), false,
                    TipoFallo.CONFIGURACION, p.problema());
        }
        ResultadoEjecucion r = remoto.probarConexion(p.solicitud());
        return new ResultadoConexion(p.idServidor(), p.hostname(), p.idCuenta(), p.cuenta(), r.simulado(), r.correcto(),
                r.fallo(), r.correcto() ? r.salida().strip() : r.error());
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
                          SolicitudEjecucion solicitud) {
    }

    /** @param fallo nulo si el acceso se verificó; si no, comunicación, autenticación, permisos o configuración */
    public record ResultadoConexion(Integer idServidor, String servidor, Integer idCuenta, String cuenta,
                                    boolean simulado, boolean correcto, TipoFallo fallo, String mensaje) {
    }
}
