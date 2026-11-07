package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.ConfiguracionMantenimiento;
import com.komainos.inventario.model.FinalidadCuenta;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ParametrosSistema;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.CuentasConfiguradas;
import com.komainos.inventario.repository.ParametrosSistemaRepositorio;
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

/** Cada configuración usa su propia cuenta de servicio o, si no tiene, la predeterminada del sistema (RF05-RF07) */
@Service
@RequiredArgsConstructor
public class ServicioAsignacionCuentas {

    private final ConfiguracionServidorRepositorio configuracionesServidor;
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final ServidorRepositorio servidores;
    private final ParametrosSistemaRepositorio parametros;
    private final PuertoCuentasServicio cuentas;
    private final ServicioAuditoria auditoria;

    @Transactional(readOnly = true)
    public Optional<CuentaEfectiva> efectiva(ConfiguracionMantenimiento configuracion, FinalidadCuenta finalidad) {
        Integer propia = propia(configuracion, finalidad);
        if (propia == null) {
            return predeterminada(finalidad);
        }
        return cuentas.nombre(propia).map(nombre -> new CuentaEfectiva(propia, nombre, false));
    }

    @Transactional(readOnly = true)
    public Optional<CuentaEfectiva> predeterminada(FinalidadCuenta finalidad) {
        Integer id = predeterminadaId(parametrosSistema(), finalidad);
        return id == null ? Optional.empty() : cuentas.nombre(id).map(nombre -> new CuentaEfectiva(id, nombre, true));
    }

    /**
     * Valida la cuenta que de verdad usará la configuración: la propia o, si no tiene, la predeterminada. No basta con
     * revisar la propia cuando cambia: un servidor que pasa a Windows y hereda una predeterminada con llave SSH quedaría
     * con una cuenta que WinRM no admite (RF06, HU04 CA3).
     */
    @Transactional(readOnly = true)
    public void validarCuentaEfectiva(Integer propia, FinalidadCuenta finalidad, FamiliaSistemaOperativo familia) {
        Integer efectiva = propia != null ? propia : predeterminadaId(parametrosSistema(), finalidad);
        if (efectiva != null) {
            cuentas.exigirAsignable(efectiva, familia);
        }
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionServidor> configuracionesDeServidores() {
        return configuracionesServidor.findAllByOrderByServidorHostnameAsc();
    }

    /** RF07: asigna la misma cuenta a varios servidores, nula los devuelve a la predeterminada */
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
            validarCuentaEfectiva(idCuenta, finalidad, configuracion.getServidor().familia());
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

    /** RF06: la predeterminada aplica a todas las familias, así que debe servir a las que la usan */
    @Transactional
    public void definirPredeterminada(Integer idCuenta, FinalidadCuenta finalidad, Actor actor) {
        ParametrosSistema sistema = parametrosSistema();
        Integer anterior = predeterminadaId(sistema, finalidad);
        if (Objects.equals(anterior, idCuenta)) {
            return;
        }
        if (idCuenta != null) {
            cuentas.exigirAsignable(idCuenta, null);
            for (FamiliaSistemaOperativo familia : familiasQueUsan(null, true, finalidad)) {
                cuentas.exigirAsignable(idCuenta, familia);
            }
        }
        if (finalidad == FinalidadCuenta.EVALUACION) {
            sistema.setIdCuentaServicioEvaluacionPredeterminada(idCuenta);
        } else {
            sistema.setIdCuentaServicioMantenimientoPredeterminada(idCuenta);
        }
        auditoria.registrar(actor, Operacion.de("DEFINIR_CUENTA_PREDETERMINADA", "configuracion_sistema", sistema.getId())
                .motivo(finalidad.name()).valores(cuenta(anterior), cuenta(idCuenta)));
    }

    @Transactional(readOnly = true)
    public UsosCuentas usos() {
        Map<Integer, Long> porServidor = aMapa(configuracionesServidor.cuentasConfiguradas());
        Map<Integer, Long> porGrupo = aMapa(configuracionesGrupo.cuentasConfiguradas());
        return new UsosCuentas(porServidor, porGrupo, parametrosSistema().getIdCuentaServicioEvaluacionPredeterminada(),
                parametrosSistema().getIdCuentaServicioMantenimientoPredeterminada());
    }

    /** Familias que dependen de la cuenta: por asignación propia y, si es la predeterminada, por omisión */
    @Transactional(readOnly = true)
    public Set<FamiliaSistemaOperativo> familiasQueUsan(Integer idCuenta) {
        Set<FamiliaSistemaOperativo> familias = EnumSet.noneOf(FamiliaSistemaOperativo.class);
        for (FinalidadCuenta finalidad : FinalidadCuenta.values()) {
            boolean predeterminada = idCuenta != null && idCuenta.equals(predeterminadaId(parametrosSistema(), finalidad));
            familias.addAll(familiasQueUsan(idCuenta, predeterminada, finalidad));
        }
        return familias;
    }

    private Set<FamiliaSistemaOperativo> familiasQueUsan(Integer idCuenta, boolean incluirSinCuenta, FinalidadCuenta finalidad) {
        Set<FamiliaSistemaOperativo> familias = EnumSet.noneOf(FamiliaSistemaOperativo.class);
        boolean evaluacion = finalidad == FinalidadCuenta.EVALUACION;
        familias.addAll(configuracionesServidor.familiasQueUsan(idCuenta, incluirSinCuenta, evaluacion));
        familias.addAll(configuracionesGrupo.familiasQueUsan(idCuenta, incluirSinCuenta, evaluacion));
        return familias;
    }

    public static Integer propia(ConfiguracionMantenimiento c, FinalidadCuenta finalidad) {
        return finalidad == FinalidadCuenta.EVALUACION ? c.getIdCuentaServicioEvaluacion() : c.getIdCuentaServicioMantenimiento();
    }

    public static Integer predeterminadaId(ParametrosSistema p, FinalidadCuenta finalidad) {
        return finalidad == FinalidadCuenta.EVALUACION ? p.getIdCuentaServicioEvaluacionPredeterminada()
                : p.getIdCuentaServicioMantenimientoPredeterminada();
    }

    private ParametrosSistema parametrosSistema() {
        return parametros.findById(ParametrosSistema.ID_UNICO)
                .orElseThrow(() -> new ReglaNegocioException("No se encontraron los parámetros del sistema"));
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

    public record CuentaEfectiva(Integer id, String nombre, boolean predeterminada) {
    }

    public record UsosCuentas(Map<Integer, Long> servidores, Map<Integer, Long> grupos, Integer idPredeterminadaEvaluacion, Integer idPredeterminadaMantenimiento) {

        public long servidoresDe(Integer idCuenta) {
            return servidores.getOrDefault(idCuenta, 0L);
        }

        public long gruposDe(Integer idCuenta) {
            return grupos.getOrDefault(idCuenta, 0L);
        }

        public boolean esPredeterminadaEvaluacion(Integer idCuenta) {
            return idCuenta != null && idCuenta.equals(idPredeterminadaEvaluacion);
        }

        public boolean esPredeterminadaMantenimiento(Integer idCuenta) {
            return idCuenta != null && idCuenta.equals(idPredeterminadaMantenimiento);
        }

        public boolean esPredeterminada(Integer idCuenta) {
            return esPredeterminadaEvaluacion(idCuenta) || esPredeterminadaMantenimiento(idCuenta);
        }
    }
}
