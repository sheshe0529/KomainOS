package com.komainos.ejecucion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.komainos.ejecucion.config.EscenarioSimulado;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.MetricaReferencia;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Modo local por defecto (DEC-44): no abre conexiones, no descifra secretos ni crea archivos. Prepara el script igual
 * que el adaptador real, así un parámetro inválido falla de la misma forma, y responde según el escenario del servidor
 * ({@link EscenarioSimulado}). Los análisis informan los valores de referencia que el dominio calcula con los umbrales
 * vigentes, por lo que el ciclo se puede probar con cualquier catálogo de métricas.
 */
@Service
@ConditionalOnProperty(prefix = "komainos.automatizacion", name = "modo", havingValue = "SIMULADO", matchIfMissing = true)
public class AnsibleSimulado implements PuertoAnsible {

    private enum Nivel { NORMAL, ADVERTENCIA, CRITICO }

    private final ObjectMapper json;
    private final PropiedadesAutomatizacion propiedades;

    public AnsibleSimulado(ObjectMapper json, PropiedadesAutomatizacion propiedades) {
        this.json = json;
        this.propiedades = propiedades;
    }

    @Override
    public ResultadoEjecucion probarConexion(SolicitudEjecucion s) {
        esperar();
        return switch (propiedades.escenario(s.hostname())) {
            case SIN_CONEXION -> falloConexion(s);
            case SIN_AUTENTICACION -> falloAutenticacion();
            case SIN_PERMISOS -> ResultadoEjecucion.fallo(true, TipoFallo.PERMISOS,
                    "la cuenta se autenticó, pero no tiene privilegios de administrador (simulado)");
            default -> ResultadoEjecucion.exito(true,
                    "Acceso verificado en %s (simulado): conectividad, autenticación y permisos de administrador"
                            .formatted(s.hostname()), List.of());
        };
    }

    @Override
    public ResultadoEjecucion ejecutar(SolicitudEjecucion s) {
        esperar();
        ScriptPreparado script = PreparadorScript.preparar(s.familia(), s.contenido(), s.parametros());
        EscenarioSimulado escenario = propiedades.escenario(s.hostname());
        if (escenario == EscenarioSimulado.SIN_CONEXION) {
            return falloConexion(s);
        }
        if (escenario == EscenarioSimulado.SIN_AUTENTICACION) {
            return falloAutenticacion();
        }
        if (s.esAnalisis()) {
            return ContratoAnalisis.aplicar(ResultadoEjecucion.exito(true, salidaAnalisis(s, escenario), List.of()), json);
        }
        if (escenario == EscenarioSimulado.SIN_PERMISOS) {
            return ResultadoEjecucion.fallo(true, TipoFallo.PERMISOS,
                    "la cuenta no tiene privilegios de administrador (simulado)");
        }
        if (escenario == EscenarioSimulado.FALLO_TAREA) {
            return ResultadoEjecucion.codigoError(true, 1, registroTarea(s, script, false),
                    "el script terminó con el código 1 (fallo simulado)");
        }
        return ResultadoEjecucion.exito(true, registroTarea(s, script, true), List.of());
    }

    /** Previa en advertencia para que haya algo que mantener; revisión y posterior normales, salvo el escenario */
    private static Nivel nivel(SolicitudEjecucion s, EscenarioSimulado escenario) {
        return switch (s.proposito()) {
            case ANALISIS_PREVIO -> escenario == EscenarioSimulado.CRITICO ? Nivel.CRITICO
                    : escenario == EscenarioSimulado.SALUDABLE ? Nivel.NORMAL : Nivel.ADVERTENCIA;
            case ANALISIS_POSTERIOR -> escenario == EscenarioSimulado.NO_CONFORME ? Nivel.ADVERTENCIA : Nivel.NORMAL;
            default -> escenario == EscenarioSimulado.CRITICO ? Nivel.CRITICO
                    : escenario == EscenarioSimulado.ADVERTENCIA ? Nivel.ADVERTENCIA : Nivel.NORMAL;
        };
    }

    private String salidaAnalisis(SolicitudEjecucion s, EscenarioSimulado escenario) {
        Nivel nivel = nivel(s, escenario);
        ObjectNode raiz = json.createObjectNode();
        ArrayNode metricas = raiz.putArray("metricas");
        for (MetricaReferencia referencia : s.referencia()) {
            MetricaReferencia.Valor valor = valor(referencia, nivel);
            ObjectNode m = metricas.addObject();
            m.put("codigo", referencia.codigo());
            if (valor.elementos() != null) {
                ArrayNode elementos = m.putArray("elementos");
                valor.elementos().forEach(elementos::add);
            } else {
                m.put("valor", valor.valor());
            }
        }
        try {
            return "Análisis simulado de %s (%s)%n%s".formatted(s.hostname(), nivel.name().toLowerCase(),
                    json.writeValueAsString(raiz));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo generar la salida simulada", ex);
        }
    }

    /** Si ningún umbral define el nivel pedido, se usa el más cercano por debajo */
    private static MetricaReferencia.Valor valor(MetricaReferencia r, Nivel nivel) {
        if (nivel == Nivel.CRITICO && r.critico() != null) {
            return r.critico();
        }
        if (nivel != Nivel.NORMAL && r.advertencia() != null) {
            return r.advertencia();
        }
        return r.normal();
    }

    private static String registroTarea(SolicitudEjecucion s, ScriptPreparado script, boolean correcto) {
        String parametros = s.parametros().isEmpty() ? "sin parámetros"
                : s.parametros().stream()
                .map(p -> p.nombre() + "=" + PreparadorScript.literal(s.familia(), p))
                .collect(Collectors.joining(", "));
        long lineas = script.contenido().lines().count();
        return String.join("\n",
                "[simulación] Servidor: %s (%s, %s)".formatted(s.hostname(), s.direccionIp(),
                        s.familia() == FamiliaSistemaOperativo.WINDOWS ? "WinRM" : "SSH"),
                "[simulación] Script: %s, %d líneas".formatted(script.nombreArchivo(), lineas),
                "[simulación] Parámetros: " + parametros,
                correcto
                        ? "[simulación] Resultado: completado sin cambios reales en el servidor"
                        : "[simulación] Resultado: el script falló antes de terminar");
    }

    private static ResultadoEjecucion falloConexion(SolicitudEjecucion s) {
        String puerto = s.familia() == FamiliaSistemaOperativo.WINDOWS ? "WinRM" : "SSH";
        return ResultadoEjecucion.fallo(true, TipoFallo.COMUNICACION,
                "%s (%s) no respondió por %s (simulado)".formatted(s.hostname(), s.direccionIp(), puerto));
    }

    private static ResultadoEjecucion falloAutenticacion() {
        return ResultadoEjecucion.fallo(true, TipoFallo.AUTENTICACION,
                "el servidor rechazó el usuario o la contraseña de la cuenta de servicio (simulado)");
    }

    private void esperar() {
        long retardo = propiedades.simulacion().retardoMs();
        if (retardo <= 0) {
            return;
        }
        try {
            Thread.sleep(retardo);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
