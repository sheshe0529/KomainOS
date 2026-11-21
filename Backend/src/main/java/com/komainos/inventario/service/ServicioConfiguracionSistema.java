package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.ConfiguracionSistema;
import com.komainos.inventario.repository.ConfiguracionSistemaRepositorio;
import com.komainos.seguridad.service.PuertoParametrosSesion;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Concurrencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioConfiguracionSistema implements PuertoParametrosSesion {

    private final ConfiguracionSistemaRepositorio repositorio;
    private final ServicioAuditoria auditoria;

    @Transactional(readOnly = true)
    public ConfiguracionSistema obtener() {
        return repositorio.findById(ConfiguracionSistema.ID_UNICO)
                .orElseThrow(() -> new ReglaNegocioException(
                        "No se encontraron los parámetros del sistema; verifique las migraciones de la base"));
    }

    @Override
    @Transactional(readOnly = true)
    public int minutosExpiracionToken() {
        return obtener().getMinutosExpiracionToken();
    }

    /** Una MOP tiene al menos una tarea: la máxima por MOP no puede ser menor que la de tarea (RF22) */
    @Transactional
    public ConfiguracionSistema actualizar(DatosParametros datos, Actor actor) {
        return actualizar(datos, null, actor);
    }

    @Transactional
    public ConfiguracionSistema actualizar(DatosParametros datos, Instant versionEsperada, Actor actor) {
        if (datos.maxDuracionMopMinutos() < datos.maxDuracionTareaMinutos()) {
            throw new ReglaNegocioException(
                    "La duración máxima por MOP no puede ser menor que la duración máxima por tarea");
        }
        ConfiguracionSistema parametros = obtener();
        Concurrencia.exigirVersion(versionEsperada, parametros.getFechaActualizacion(), "los parámetros del sistema");
        Map<String, Object> anterior = instantanea(parametros);

        parametros.setMaxEjecucionesConcurrentes(datos.maxEjecucionesConcurrentes());
        parametros.setMaxDuracionTareaMinutos(datos.maxDuracionTareaMinutos());
        parametros.setMaxDuracionMopMinutos(datos.maxDuracionMopMinutos());
        parametros.setMinutosExpiracionToken(datos.minutosExpiracionToken());

        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_PARAMETROS", "configuracion_sistema", parametros.getId())
                .valores(anterior, instantanea(parametros)));
        return parametros;
    }

    private static Map<String, Object> instantanea(ConfiguracionSistema p) {
        return Map.of(
                "maxEjecucionesConcurrentes", p.getMaxEjecucionesConcurrentes(),
                "maxDuracionTareaMinutos", p.getMaxDuracionTareaMinutos(),
                "maxDuracionMopMinutos", p.getMaxDuracionMopMinutos(),
                "minutosExpiracionToken", p.getMinutosExpiracionToken());
    }

    public record DatosParametros(int maxEjecucionesConcurrentes, int maxDuracionTareaMinutos,
                                  int maxDuracionMopMinutos, int minutosExpiracionToken) {
    }
}
