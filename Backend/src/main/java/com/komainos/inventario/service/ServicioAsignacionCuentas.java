package com.komainos.inventario.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.ConfiguracionMantenimiento;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ParametrosSistema;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.ConteoPorCuenta;
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
    public Optional<CuentaEfectiva> efectiva(ConfiguracionMantenimiento configuracion) {
        Integer propia = configuracion.getIdCuentaServicio();
        if (propia == null) {
            return predeterminada();
        }
        return cuentas.nombre(propia).map(nombre -> new CuentaEfectiva(propia, nombre, false));
    }

    @Transactional(readOnly = true)
    public Optional<CuentaEfectiva> predeterminada() {
        Integer id = parametrosSistema().getIdCuentaServicioPredeterminada();
        return id == null ? Optional.empty() : cuentas.nombre(id).map(nombre -> new CuentaEfectiva(id, nombre, true));
    }

    /** Solo se valida la cuenta cuando cambia, igual que las demás referencias de la configuración */
    public void validarCambio(Integer actual, Integer nueva, FamiliaSistemaOperativo familia) {
        if (nueva != null && !nueva.equals(actual)) {
            cuentas.exigirAsignable(nueva, familia);
        }
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionServidor> configuracionesDeServidores() {
        return configuracionesServidor.findAllByOrderByServidorHostnameAsc();
    }

    /** RF07: asigna la misma cuenta a varios servidores, nula los devuelve a la predeterminada */
    @Transactional
    public int asignarAServidores(Integer idCuenta, Collection<Integer> idsServidores, Actor actor) {
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
            Integer anterior = configuracion.getIdCuentaServicio();
            if (Objects.equals(anterior, idCuenta)) {
                continue;
            }
            if (idCuenta != null) {
                cuentas.exigirAsignable(idCuenta, configuracion.getServidor().familia());
            }
            configuracion.setIdCuentaServicio(idCuenta);
            auditoria.registrar(actor, Operacion.de("ASIGNAR_CUENTA_SERVICIO", "configuracion_servidor",
                            configuracion.getId())
                    .sobreServidor(id)
                    .valores(cuenta(anterior), cuenta(idCuenta)));
        }
        return ids.size();
    }

    /** RF06: la predeterminada aplica a todas las familias, así que debe servir a las que la usan */
    @Transactional
    public void definirPredeterminada(Integer idCuenta, Actor actor) {
        ParametrosSistema sistema = parametrosSistema();
        Integer anterior = sistema.getIdCuentaServicioPredeterminada();
        if (Objects.equals(anterior, idCuenta)) {
            return;
        }
        if (idCuenta != null) {
            cuentas.exigirAsignable(idCuenta, null);
            for (FamiliaSistemaOperativo familia : familiasQueUsan(null, true)) {
                cuentas.exigirAsignable(idCuenta, familia);
            }
        }
        sistema.setIdCuentaServicioPredeterminada(idCuenta);
        auditoria.registrar(actor, Operacion.de("DEFINIR_CUENTA_PREDETERMINADA", "configuracion_sistema", sistema.getId())
                .valores(cuenta(anterior), cuenta(idCuenta)));
    }

    @Transactional(readOnly = true)
    public UsosCuentas usos() {
        Map<Integer, Long> porServidor = aMapa(configuracionesServidor.contarPorCuenta());
        Map<Integer, Long> porGrupo = aMapa(configuracionesGrupo.contarPorCuenta());
        return new UsosCuentas(porServidor, porGrupo, parametrosSistema().getIdCuentaServicioPredeterminada());
    }

    /** Familias que dependen de la cuenta: por asignación propia y, si es la predeterminada, por omisión */
    @Transactional(readOnly = true)
    public Set<FamiliaSistemaOperativo> familiasQueUsan(Integer idCuenta) {
        boolean esPredeterminada = idCuenta != null
                && idCuenta.equals(parametrosSistema().getIdCuentaServicioPredeterminada());
        return familiasQueUsan(idCuenta, esPredeterminada);
    }

    private Set<FamiliaSistemaOperativo> familiasQueUsan(Integer idCuenta, boolean incluirSinCuenta) {
        Set<FamiliaSistemaOperativo> familias = EnumSet.noneOf(FamiliaSistemaOperativo.class);
        familias.addAll(configuracionesServidor.familiasQueUsan(idCuenta, incluirSinCuenta));
        familias.addAll(configuracionesGrupo.familiasQueUsan(idCuenta, incluirSinCuenta));
        return familias;
    }

    private ParametrosSistema parametrosSistema() {
        return parametros.findById(ParametrosSistema.ID_UNICO)
                .orElseThrow(() -> new ReglaNegocioException("No se encontraron los parámetros del sistema"));
    }

    private static Map<Integer, Long> aMapa(List<ConteoPorCuenta> conteos) {
        Map<Integer, Long> mapa = new HashMap<>();
        conteos.forEach(c -> mapa.put(c.idCuentaServicio(), c.cantidad()));
        return mapa;
    }

    private static Map<String, Object> cuenta(Integer id) {
        Map<String, Object> valor = new HashMap<>();
        valor.put("idCuentaServicio", id);
        return valor;
    }

    public record CuentaEfectiva(Integer id, String nombre, boolean predeterminada) {
    }

    public record UsosCuentas(Map<Integer, Long> servidores, Map<Integer, Long> grupos, Integer idPredeterminada) {

        public long servidoresDe(Integer idCuenta) {
            return servidores.getOrDefault(idCuenta, 0L);
        }

        public long gruposDe(Integer idCuenta) {
            return grupos.getOrDefault(idCuenta, 0L);
        }

        public boolean esPredeterminada(Integer idCuenta) {
            return idCuenta != null && idCuenta.equals(idPredeterminada);
        }
    }
}
