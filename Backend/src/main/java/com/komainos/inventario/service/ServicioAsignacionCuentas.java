package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.ConfiguracionMantenimiento;
import com.komainos.inventario.model.FinalidadCuenta;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.CuentasConfiguradas;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cada configuración usa la cuenta de servicio que tiene asignada para cada finalidad (RF05, RF07). No hay cuenta
 * predeterminada (DEC-60): sin asignación, la configuración no tiene con qué conectarse para esa finalidad.
 */
@Service
@RequiredArgsConstructor
public class ServicioAsignacionCuentas {

    private final ConfiguracionServidorRepositorio configuracionesServidor;
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final ServidorRepositorio servidores;
    private final PuertoCuentasServicio cuentas;
    private final ServicioAuditoria auditoria;

    @Transactional(readOnly = true)
    public Optional<CuentaAsignada> asignada(ConfiguracionMantenimiento configuracion, FinalidadCuenta finalidad) {
        Integer propia = propia(configuracion, finalidad);
        return propia == null ? Optional.empty() : cuentas.nombre(propia).map(nombre -> new CuentaAsignada(propia, nombre));
    }

    /** La cuenta asignada debe servir a la familia del servidor o grupo (RF05, HU04 CA3) */
    @Transactional(readOnly = true)
    public void validarCuenta(Integer idCuenta, FamiliaSistemaOperativo familia) {
        if (idCuenta != null) {
            cuentas.exigirAsignable(idCuenta, familia);
        }
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionServidor> configuracionesDeServidores() {
        return configuracionesServidor.findAllByOrderByServidorHostnameAsc();
    }

    /** RF07: asigna la misma cuenta a varios servidores; nula les quita la asignación de esa finalidad */
    @Transactional
    public int asignarAServidores(Integer idCuenta, Collection<Integer> idsServidores, FinalidadCuenta finalidad, Actor actor) {
        Set<Integer> ids = new LinkedHashSet<>(idsServidores);
        Map<Integer, ConfiguracionServidor> porServidor = configuracionesServidor.findByServidorIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getServidor().getId(), Function.identity()));
        for (Integer id : ids) {
            if (!porServidor.containsKey(id)) {
                Servidor servidor = servidores.findById(id)
                        .orElseThrow(() -> RecursoNoEncontradoException.de("el servidor", id));
                throw new ReglaNegocioException(("El servidor %s no tiene configuración de mantenimiento: "
                        + "configúrelo antes de asignarle una cuenta de servicio").formatted(servidor.getHostname()));
            }
        }
        for (Integer id : ids) {
            ConfiguracionServidor configuracion = porServidor.get(id);
            Integer anterior = propia(configuracion, finalidad);
            if (Objects.equals(anterior, idCuenta)) {
                continue;
            }
            validarCuenta(idCuenta, configuracion.getServidor().familia());
            if (finalidad == FinalidadCuenta.EVALUACION) {
                configuracion.setIdCuentaServicioEvaluacion(idCuenta);
            } else {
                configuracion.setIdCuentaServicioMantenimiento(idCuenta);
            }
            auditoria.registrar(actor, Operacion.de("ASIGNAR_CUENTA_SERVICIO", "configuracion_servidor",
                            configuracion.getId())
                    .sobreServidor(id)
                    .motivo(finalidad.name()).valores(cuenta(anterior), cuenta(idCuenta)));
        }
        return ids.size();
    }

    @Transactional(readOnly = true)
    public UsosCuentas usos() {
        return new UsosCuentas(aMapa(configuracionesServidor.cuentasConfiguradas()),
                aMapa(configuracionesGrupo.cuentasConfiguradas()));
    }

    /** Familias de los servidores y grupos que tienen asignada la cuenta, para cualquier finalidad */
    @Transactional(readOnly = true)
    public Set<FamiliaSistemaOperativo> familiasQueUsan(Integer idCuenta) {
        Set<FamiliaSistemaOperativo> familias = EnumSet.noneOf(FamiliaSistemaOperativo.class);
        for (FinalidadCuenta finalidad : FinalidadCuenta.values()) {
            boolean evaluacion = finalidad == FinalidadCuenta.EVALUACION;
            familias.addAll(configuracionesServidor.familiasQueUsan(idCuenta, evaluacion));
            familias.addAll(configuracionesGrupo.familiasQueUsan(idCuenta, evaluacion));
        }
        return familias;
    }

    public static Integer propia(ConfiguracionMantenimiento c, FinalidadCuenta finalidad) {
        return finalidad == FinalidadCuenta.EVALUACION ? c.getIdCuentaServicioEvaluacion() : c.getIdCuentaServicioMantenimiento();
    }

    private static Map<Integer, Long> aMapa(List<CuentasConfiguradas> conteos) {
        Map<Integer, Long> mapa = new HashMap<>();
        for (CuentasConfiguradas c : conteos) {
            Set<Integer> ids = new LinkedHashSet<>();
            if (c.evaluacion() != null) ids.add(c.evaluacion());
            if (c.mantenimiento() != null) ids.add(c.mantenimiento());
            ids.forEach(id -> mapa.merge(id, 1L, Long::sum));
        }
        return mapa;
    }

    private static Map<String, Object> cuenta(Integer id) {
        Map<String, Object> valor = new HashMap<>();
        valor.put("idCuentaServicio", id);
        return valor;
    }

    public record CuentaAsignada(Integer id, String nombre) {
    }

    public record UsosCuentas(Map<Integer, Long> servidores, Map<Integer, Long> grupos) {

        public long servidoresDe(Integer idCuenta) {
            return servidores.getOrDefault(idCuenta, 0L);
        }

        public long gruposDe(Integer idCuenta) {
            return grupos.getOrDefault(idCuenta, 0L);
        }
    }
}
