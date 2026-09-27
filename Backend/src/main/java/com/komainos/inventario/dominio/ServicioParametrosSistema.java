package com.komainos.inventario.dominio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.inventario.infra.ParametrosSistemaRepositorio;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.error.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Parametros de ejecucion (RF68) y vigencia de la sesion (RF02).
 */
@Service
@RequiredArgsConstructor
public class ServicioParametrosSistema {

    private final ParametrosSistemaRepositorio repositorio;
    private final ServicioAuditoria auditoria;

    @Transactional(readOnly = true)
    public ParametrosSistema obtener() {
        return repositorio.findById(ParametrosSistema.ID_UNICO)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No se encontraron los parámetros del sistema; verifique las migraciones de la base"));
    }

    /**
     * Actualiza los parametros globales. RF22 exige que la duracion estimada
     * de una tarea no supere la maxima por tarea, y una MOP contiene al menos
     * una tarea: por eso la maxima por MOP no puede ser menor que la de tarea.
     */
    @Transactional
    public ParametrosSistema actualizar(DatosParametros datos, Actor actor) {
        if (datos.maxDuracionMopMinutos() < datos.maxDuracionTareaMinutos()) {
            throw new ReglaNegocioException(
                    "La duración máxima por MOP no puede ser menor que la duración máxima por tarea");
        }
        ParametrosSistema parametros = obtener();
        Map<String, Object> anterior = instantanea(parametros);

        parametros.setMaxEjecucionesConcurrentes(datos.maxEjecucionesConcurrentes());
        parametros.setMaxDuracionTareaMinutos(datos.maxDuracionTareaMinutos());
        parametros.setMaxDuracionMopMinutos(datos.maxDuracionMopMinutos());
        parametros.setMinCiclosRachaEstable(datos.minCiclosRachaEstable());
        parametros.setMinutosExpiracionToken(datos.minutosExpiracionToken());

        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_PARAMETROS", "configuracion_sistema", parametros.getId())
                .valores(anterior, instantanea(parametros)));
        return parametros;
    }

    private static Map<String, Object> instantanea(ParametrosSistema p) {
        return Map.of(
                "maxEjecucionesConcurrentes", p.getMaxEjecucionesConcurrentes(),
                "maxDuracionTareaMinutos", p.getMaxDuracionTareaMinutos(),
                "maxDuracionMopMinutos", p.getMaxDuracionMopMinutos(),
                "minCiclosRachaEstable", p.getMinCiclosRachaEstable(),
                "minutosExpiracionToken", p.getMinutosExpiracionToken());
    }

    public record DatosParametros(int maxEjecucionesConcurrentes, int maxDuracionTareaMinutos,
                                  int maxDuracionMopMinutos, int minCiclosRachaEstable,
                                  int minutosExpiracionToken) {
    }
}
