package com.komainos.inventario.service;

import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OrigenUmbral;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralAplicado;
import com.komainos.inventario.model.UmbralAplicado.ResultadoLista;
import com.komainos.inventario.model.UmbralExcepcion;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Umbrales que rigen para un servidor y prueba de una medición contra ellos, sin registrar nada (RF16, R2.4). El
 * responsable solo ve sus servidores; uno ajeno responde 404.
 */
@Service
@RequiredArgsConstructor
public class ServicioUmbralesServidor {

    /** Lo que informa el usuario al probar: separado por comas, punto y coma o saltos de línea */
    private static final Pattern SEPARADORES = Pattern.compile("[,;\\r\\n]+");
    /** Lo que se prueba es lo que informaría un script: se acota antes de procesarlo */
    private static final int MAXIMO_CARACTERES = 20_000;
    private static final int MAXIMO_ELEMENTOS = 1_000;

    private final ServicioServidor servidores;
    private final UmbralRepositorio umbrales;
    private final GrupoMantenimientoRepositorio grupos;

    /**
     * Como mantenimiento individual, sin nivel de grupo: es lo que aplica a una revisión periódica o a una orden del
     * servidor. Aparte, lo que aplicaría dentro de cada grupo del servidor que tenga su propia excepción.
     */
    @Transactional(readOnly = true)
    public List<UmbralDelServidor> aplicables(Integer idServidor, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        List<GrupoMantenimiento> gruposDelServidor = grupos.findDelServidor(idServidor);
        List<Umbral> vigentes = umbrales.findAplicables();
        vigentes.forEach(ServicioUmbralesServidor::cargar);
        List<UmbralDelServidor> resultado = vigentes.stream()
                .map(u -> new UmbralDelServidor(UmbralAplicado.resolver(u, ContextoEvaluacion.individual(servidor)),
                        gruposDelServidor.stream()
                                .map(g -> new EnGrupo(g, UmbralAplicado.resolver(u, ContextoEvaluacion.enGrupo(servidor, g.getId()))))
                                .filter(e -> e.aplicado().origen() == OrigenUmbral.GRUPO)
                                .toList()))
                .toList();
        resultado.forEach(r -> {
            cargar(r.aplicado());
            r.enGrupos().forEach(e -> cargar(e.aplicado()));
        });
        return resultado;
    }

    /**
     * Evalúa un valor, o los elementos informados en una métrica de lista, con lo que rige para el servidor. Con idGrupo
     * se prueba como integrante de una orden de ese grupo.
     */
    @Transactional(readOnly = true)
    public ResultadoPrueba probar(Integer idServidor, Integer idUmbral, BigDecimal valor, String elementos,
                                  Integer idGrupo, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        Umbral umbral = umbrales.findConMetricaById(idUmbral)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el umbral", idUmbral));
        if (!umbral.isActivo() || !umbral.getMetrica().isActivo()) {
            throw new ReglaNegocioException("El umbral %s no se evalúa: está inactivo o su métrica lo está"
                    .formatted(umbral.getNombre()));
        }
        cargar(umbral);
        ContextoEvaluacion contexto = ContextoEvaluacion.individual(servidor);
        if (idGrupo != null) {
            GrupoMantenimiento grupo = grupos.findDelServidor(idServidor).stream()
                    .filter(g -> g.getId().equals(idGrupo)).findFirst()
                    .orElseThrow(() -> new ReglaNegocioException("El servidor %s no integra ese grupo de mantenimiento"
                            .formatted(servidor.getHostname())));
            contexto = ContextoEvaluacion.enGrupo(servidor, grupo.getId());
        }
        UmbralAplicado aplicado = UmbralAplicado.resolver(umbral, contexto);
        cargar(aplicado);

        if (umbral.esDeLista()) {
            if (elementos != null && elementos.length() > MAXIMO_CARACTERES) {
                throw new ReglaNegocioException("Los elementos a probar no pueden superar los %d caracteres"
                        .formatted(MAXIMO_CARACTERES));
            }
            List<String> informados = elementos == null ? List.of()
                    : Arrays.stream(SEPARADORES.split(elementos)).map(String::trim).filter(e -> !e.isEmpty()).toList();
            if (informados.size() > MAXIMO_ELEMENTOS) {
                throw new ReglaNegocioException("Se pueden probar hasta %d elementos".formatted(MAXIMO_ELEMENTOS));
            }
            ResultadoLista resultado = aplicado.evaluarLista(informados);
            return new ResultadoPrueba(aplicado, resultado.nivel(), null, resultado.faltantes(), resultado.sobrantes());
        }
        if (valor == null) {
            throw new ReglaNegocioException("Indique el valor que quiere probar");
        }
        // Mismo rango que una medición (numeric(20,6)) y que el tipo de la métrica: acota también el valor que se devuelve
        umbral.getMetrica().getTipoDato().validarLimite(valor, "a probar");
        return new ResultadoPrueba(aplicado, aplicado.evaluar(valor), valor, List.of(), List.of());
    }

    /** Para resolver basta el id de cada objetivo, que no inicializa el proxy */
    private static void cargar(Umbral umbral) {
        Hibernate.initialize(umbral.getExcepciones());
        if (umbral.getLista() != null) {
            Hibernate.initialize(umbral.getLista().getItems());
        }
    }

    /** El mapeo muestra el objetivo de la excepción que se aplicó y los elementos de su lista */
    private static void cargar(UmbralAplicado aplicado) {
        UmbralExcepcion e = aplicado.excepcion();
        if (e != null) {
            Hibernate.initialize(e.getServidor());
            Hibernate.initialize(e.getGrupo());
            Hibernate.initialize(e.getEntorno());
            Hibernate.initialize(e.getNivelCriticidad());
        }
        if (aplicado.lista() != null) {
            Hibernate.initialize(aplicado.lista().getItems());
        }
    }

    public record UmbralDelServidor(UmbralAplicado aplicado, List<EnGrupo> enGrupos) {
    }

    public record EnGrupo(GrupoMantenimiento grupo, UmbralAplicado aplicado) {
    }

    /** valor solo en métricas numéricas; faltantes y sobrantes solo en las de lista */
    public record ResultadoPrueba(UmbralAplicado aplicado, NivelAlerta nivel, BigDecimal valor, List<String> faltantes,
                                  List<String> sobrantes) {
    }
}
