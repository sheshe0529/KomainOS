package com.komainos.mantenimiento.service;

import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.ejecucion.model.MedicionInformada;
import com.komainos.ejecucion.model.MetricaReferencia;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralAplicado;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.EvaluacionUmbral;
import com.komainos.mantenimiento.model.MedicionMetrica;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ejecución de los scripts de análisis (RF37, RF38, RF55) en tres pasos: {@link #iniciar} registra la evaluación en
 * curso, el adaptador ejecuta el script fuera de cualquier transacción y {@link #registrar} guarda las mediciones con
 * los umbrales aplicados. Así una conexión lenta no mantiene abierta una transacción ni bloquea el ciclo.
 */
@Service
@RequiredArgsConstructor
public class ServicioEvaluaciones {

    private final ServicioCatalogoAutomatizacion catalogo;
    private final PuertoCuentasServicio cuentas;
    private final ServicioParametrosSistema parametros;
    private final ServicioServidor servidores;
    private final UmbralRepositorio umbrales;
    private final MetricaRepositorio metricas;
    private final EvaluacionRepositorio evaluaciones;
    private final Clock reloj;

    /**
     * Comprueba que el servidor se pueda analizar y deja la evaluación EN_CURSO. Sin cuenta propia usa la cuenta
     * predeterminada (RF06).
     *
     * @throws ReglaNegocioException si falta la cuenta o el script de análisis compatible (RF24); antes de escribir
     *                               nada, así quien prepara varios servidores registra el motivo y sigue con los demás
     */
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public Evaluacion iniciar(Servidor servidor, OrdenDetalle detalle, TipoEvaluacion tipo, Integer idCuenta) {
        Integer cuenta = cuentaAplicable(idCuenta);
        if (cuenta == null) {
            throw new ReglaNegocioException(("Asigne una cuenta de servicio al servidor %s o defina la cuenta "
                    + "predeterminada para analizarlo").formatted(servidor.getHostname()));
        }
        cuentas.exigirAsignable(cuenta, servidor.familia());
        VersionScript version = catalogo.buscarAnalisis(servidor.getVersionSistemaOperativo().getId())
                .orElseThrow(() -> new ReglaNegocioException(
                        "No hay un script de análisis activo y habilitado para %s (servidor %s)".formatted(
                                servidor.getVersionSistemaOperativo().descripcionCompleta(), servidor.getHostname())));
        return evaluaciones.save(Evaluacion.iniciar(servidor, detalle, version, cuenta, tipo, Tiempo.ahora(reloj)));
    }

    /** Todo lo que el adaptador necesita, armado antes de salir de la transacción */
    @Transactional(readOnly = true)
    public SolicitudEjecucion solicitud(Integer idEvaluacion) {
        Evaluacion e = buscar(idEvaluacion);
        Servidor s = e.getServidor();
        VersionScript version = catalogo.version(e.getVersion().getId());
        PropositoEjecucion proposito = switch (e.getTipo()) {
            case REVISION_PERIODICA -> PropositoEjecucion.ANALISIS_REVISION;
            case PREVIA -> PropositoEjecucion.ANALISIS_PREVIO;
            case POSTERIOR -> PropositoEjecucion.ANALISIS_POSTERIOR;
        };
        return new SolicitudEjecucion(s.getId(), s.getHostname(), s.getDireccionIp(), s.familia(),
                e.getIdCuentaServicio(), proposito, version.getContenido(),
                ServicioCatalogoAutomatizacion.valoresDeEjecucion(version), maxMinutos(),
                referencia(contexto(s, e.getDetalle())));
    }

    /**
     * Guarda el resultado. Si el análisis no cumple el contrato o falta una métrica que exige un umbral activo, la
     * evaluación queda FALLIDA sin mediciones parciales: no es concluyente (CP103).
     */
    @Transactional
    public Evaluacion registrar(Integer idEvaluacion, ResultadoEjecucion resultado) {
        Evaluacion e = buscar(idEvaluacion);
        Instant ahora = Tiempo.ahora(reloj);
        if (e.getEstado() != EstadoEvaluacion.EN_CURSO) {
            return e;
        }
        if (!resultado.correcto()) {
            e.fallar(resultado.error(), ahora);
            return e;
        }
        try {
            registrarMediciones(e, resultado.metricas(), ahora);
            e.finalizar(ahora);
        } catch (ReglaNegocioException ex) {
            e.fallar(TipoFallo.CONTRATO.describir(ex.getMessage()), ahora);
        }
        return e;
    }

    /** Recuperación tras un reinicio: el análisis en curso no tiene resultado conocido */
    @Transactional
    public void interrumpir(Integer idEvaluacion, String motivo) {
        Evaluacion e = buscar(idEvaluacion);
        if (e.getEstado() == EstadoEvaluacion.EN_CURSO) {
            e.fallar(motivo, Tiempo.ahora(reloj));
        }
    }

    @Transactional(readOnly = true)
    public Page<Evaluacion> historial(Integer idServidor, AlcanceUsuario alcance, Pageable pagina) {
        servidores.obtener(idServidor, alcance);
        Page<Evaluacion> resultado = evaluaciones.findByServidorIdOrderByFechaInicioDesc(idServidor, pagina);
        resultado.forEach(ServicioEvaluaciones::cargar);
        return resultado;
    }

    @Transactional(readOnly = true)
    public Evaluacion obtener(Integer idEvaluacion) {
        Evaluacion e = buscar(idEvaluacion);
        cargar(e);
        return e;
    }

    /** Valores esperados de cada métrica según los umbrales del servidor; los usa el modo simulado */
    @Transactional(readOnly = true)
    public List<MetricaReferencia> referencia(ContextoEvaluacion contexto) {
        return ValoresReferencia.calcular(metricas.findAllByOrderByNombreAsc(), umbrales.findAplicables(), contexto);
    }

    public Integer cuentaAplicable(Integer idCuenta) {
        return idCuenta != null ? idCuenta : parametros.obtener().getIdCuentaServicioPredeterminada();
    }

    public int maxMinutos() {
        return parametros.obtener().getMaxDuracionTareaMinutos();
    }

    /** El grupo cuenta solo si la orden es grupal: en la revisión y en la orden individual no hay grupo (DEC-41) */
    public static ContextoEvaluacion contexto(Servidor servidor, OrdenDetalle detalle) {
        if (detalle == null || !detalle.getOrden().esGrupal()) {
            return ContextoEvaluacion.individual(servidor);
        }
        return ContextoEvaluacion.enGrupo(servidor, detalle.getOrden().getGrupo().getId());
    }

    public static void cargar(Evaluacion e) {
        Hibernate.initialize(e.getVersion().getImplementacion().getScript());
        e.getMediciones().forEach(m -> {
            Hibernate.initialize(m.getMetrica());
            Hibernate.initialize(m.getElementos());
            Hibernate.initialize(m.getUmbrales());
        });
    }

    private void registrarMediciones(Evaluacion e, List<MedicionInformada> informadas, Instant ahora) {
        Map<String, Metrica> catalogoMetricas = metricas.findAllByOrderByNombreAsc().stream()
                .filter(Metrica::isActivo)
                .collect(Collectors.toMap(Metrica::getCodigo, Function.identity(), (a, b) -> a));
        List<Umbral> vigentes = umbrales.findAplicables();
        ContextoEvaluacion contexto = contexto(e.getServidor(), e.getDetalle());
        Set<String> recibidas = new LinkedHashSet<>();

        for (MedicionInformada dato : informadas) {
            Metrica metrica = catalogoMetricas.get(dato.codigo());
            if (metrica == null) {
                // Una métrica que el catálogo no conoce no se evalúa: el script puede informar más de lo necesario
                continue;
            }
            recibidas.add(dato.codigo());
            MedicionMetrica medicion = medir(e, metrica, dato);
            for (Umbral umbral : vigentes) {
                if (!umbral.getMetrica().getId().equals(metrica.getId())) {
                    continue;
                }
                UmbralAplicado aplicado = UmbralAplicado.resolver(umbral, contexto);
                NivelAlerta nivel = umbral.esDeLista()
                        ? aplicado.evaluarLista(medicion.getElementos()).nivel()
                        : aplicado.evaluar(medicion.getValor());
                medicion.agregarUmbral(EvaluacionUmbral.de(medicion, aplicado, nivel, ahora));
            }
            e.agregarMedicion(medicion);
        }

        List<String> faltantes = vigentes.stream()
                .map(u -> u.getMetrica().getCodigo())
                .filter(codigo -> !recibidas.contains(codigo))
                .distinct()
                .toList();
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException("el análisis no informó %s, que exige%s un umbral activo"
                    .formatted(String.join(", ", faltantes), faltantes.size() == 1 ? "" : "n"));
        }
        if (e.getMediciones().isEmpty()) {
            throw new ReglaNegocioException("el análisis no informó ninguna métrica activa del catálogo");
        }
    }

    private static MedicionMetrica medir(Evaluacion e, Metrica metrica, MedicionInformada dato) {
        if (metrica.isEsLista() != (dato.elementos() != null)) {
            throw new ReglaNegocioException(metrica.isEsLista()
                    ? "la métrica «%s» es de lista: informe «elementos»".formatted(dato.codigo())
                    : "la métrica «%s» es de valor único: informe «valor»".formatted(dato.codigo()));
        }
        if (metrica.isEsLista()) {
            Set<String> elementos = new LinkedHashSet<>();
            dato.elementos().forEach(x -> elementos.add(metrica.getTipoDato().canonico(x, false)));
            return MedicionMetrica.lista(e, metrica, dato.instancia(), elementos);
        }
        BigDecimal valor = dato.valor();
        metrica.getTipoDato().validarLimite(valor, "informado de «%s»".formatted(dato.codigo()));
        return MedicionMetrica.escalar(e, metrica, dato.instancia(), valor);
    }

    private Evaluacion buscar(Integer id) {
        return evaluaciones.findConDetalleById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la evaluación", id));
    }
}
