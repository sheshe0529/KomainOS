package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.ImplementacionScript;
import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.RevisionSalida;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.EjecutorRemoto;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prueba de una versión de análisis sobre un servidor desde el catálogo, antes de usarla en el ciclo. Solo análisis:
 * son de lectura, mientras que un script de mantenimiento cambia el servidor y debe pasar por la MOP y la política de
 * cambio (RF45). La prueba no registra una evaluación ni afecta indicadores, pero queda en la auditoría.
 */
@Service
public class ServicioPruebaAnalisis {

    private final ServicioCatalogoAutomatizacion catalogo;
    private final ServicioServidor servidores;
    private final ConfiguracionServidorRepositorio configuraciones;
    private final PuertoCuentasServicio cuentas;
    private final ServicioEvaluaciones evaluaciones;
    private final EjecutorRemoto remoto;
    private final ServicioAuditoria auditoria;
    private final TransactionTemplate transaccion;

    public ServicioPruebaAnalisis(ServicioCatalogoAutomatizacion catalogo, ServicioServidor servidores,
                                  ConfiguracionServidorRepositorio configuraciones, PuertoCuentasServicio cuentas,
                                  ServicioEvaluaciones evaluaciones, EjecutorRemoto remoto, ServicioAuditoria auditoria,
                                  PlatformTransactionManager transacciones) {
        this.catalogo = catalogo;
        this.servidores = servidores;
        this.configuraciones = configuraciones;
        this.cuentas = cuentas;
        this.evaluaciones = evaluaciones;
        this.remoto = remoto;
        this.auditoria = auditoria;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    public Prueba probar(Integer idVersion, Integer idServidor, Actor actor) {
        Preparada preparada = transaccion.execute(estado -> {
            VersionScript version = catalogo.version(idVersion);
            ImplementacionScript implementacion = version.getImplementacion();
            if (!implementacion.getScript().esAnalisis()) {
                throw new ReglaNegocioException("Solo se prueban scripts de análisis: un script de mantenimiento cambia el "
                        + "servidor y se ejecuta únicamente desde una MOP autorizada");
            }
            Servidor servidor = servidores.obtenerSinAlcance(idServidor);
            if (servidor.familia() != implementacion.familia()) {
                throw new ReglaNegocioException("La versión es para %s y el servidor %s usa %s".formatted(
                        implementacion.getVersionSistemaOperativo().descripcionCompleta(), servidor.getHostname(),
                        servidor.getVersionSistemaOperativo().descripcionCompleta()));
            }
            Integer cuenta = evaluaciones.cuentaAplicable(configuraciones.findByServidorId(idServidor)
                    .map(c -> c.getIdCuentaServicio()).orElse(null));
            if (cuenta == null) {
                throw new ReglaNegocioException("Asigne una cuenta de servicio al servidor o defina la predeterminada");
            }
            cuentas.exigirAsignable(cuenta, servidor.familia());
            String advertencia = servidor.getVersionSistemaOperativo().getId().equals(implementacion.getVersionSistemaOperativo().getId())
                    ? null
                    : "La implementación es para %s y el servidor tiene %s: el ciclo no la usará con este servidor"
                    .formatted(implementacion.getVersionSistemaOperativo().descripcionCompleta(),
                            servidor.getVersionSistemaOperativo().descripcionCompleta());
            SolicitudEjecucion solicitud = new SolicitudEjecucion(servidor.getId(), servidor.getHostname(),
                    servidor.getDireccionIp(), servidor.familia(), cuenta, PropositoEjecucion.PRUEBA_ANALISIS,
                    version.getContenido(), ServicioCatalogoAutomatizacion.valoresDeEjecucion(version),
                    evaluaciones.maxMinutos(), evaluaciones.referencia(ContextoEvaluacion.individual(servidor)));
            return new Preparada(solicitud, advertencia, "%s v%d".formatted(implementacion.getScript().getNombre(),
                    version.getNumeroVersion()));
        });
        ResultadoEjecucion resultado = remoto.ejecutar(preparada.solicitud());
        RevisionSalida revision = resultado.correcto() || resultado.fallo() == TipoFallo.CONTRATO
                ? catalogo.revisarSalida(resultado.salida())
                : null;
        transaccion.executeWithoutResult(estado -> auditoria.registrar(actor,
                Operacion.de("PROBAR_SCRIPT_ANALISIS", "script_version", idVersion).sobreServidor(idServidor)
                        .motivo("%s en %s: %s".formatted(preparada.descripcion(), preparada.solicitud().hostname(),
                                resultado.correcto() ? "correcto" : resultado.fallo()))));
        return new Prueba(resultado.simulado(), resultado.correcto(), resultado.fallo(), resultado.error(),
                resultado.salida(), revision, preparada.advertencia());
    }

    private record Preparada(SolicitudEjecucion solicitud, String advertencia, String descripcion) {
    }

    /**
     * @param revision métricas reconocidas y problemas de la salida; nula si el script no llegó a ejecutarse
     */
    public record Prueba(boolean simulado, boolean correcto, TipoFallo fallo, String error, String salida,
                         RevisionSalida revision, String advertencia) {
    }
}
