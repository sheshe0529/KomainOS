package com.komainos.ejecucion.simulacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.ContratoAnalisis;
import com.komainos.ejecucion.service.PreparadorScript;
import com.komainos.ejecucion.service.PuertoAnsible;
import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Doble de pruebas del adaptador Ansible: no abre conexiones, no descifra secretos ni crea archivos. Prepara el script
 * igual que el adaptador real, así un parámetro inválido falla de la misma forma, y responde según el escenario del
 * servidor ({@link EscenarioSimulado}). Los análisis informan los valores de referencia que producen cada nivel con los
 * umbrales vigentes del servidor ({@link ValoresReferencia}), así el ciclo se prueba con cualquier catálogo de métricas.
 * Solo existe en las pruebas: el backend desplegado ejecuta siempre con {@code AnsibleReal}.
 */
public class AnsibleSimulado implements PuertoAnsible {

    private enum Nivel { NORMAL, ADVERTENCIA, CRITICO }

    private final ObjectMapper json;
    private final Map<String, EscenarioSimulado> escenarios;
    private final Function<SolicitudEjecucion, List<MetricaReferencia>> referencias;

    /**
     * @param escenarios  hostname y comportamiento que se reproduce para ese servidor; los demás usan ESTANDAR
     * @param referencias valores de cada métrica por nivel para el servidor del análisis
     */
    public AnsibleSimulado(ObjectMapper json, Map<String, String> escenarios,
                           Function<SolicitudEjecucion, List<MetricaReferencia>> referencias) {
        this.json = json;
        // El hostname se compara sin distinguir mayúsculas: el inventario los guarda en mayúsculas
        this.escenarios = escenarios.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                e -> e.getKey().toLowerCase(Locale.ROOT),
                e -> EscenarioSimulado.valueOf(e.getValue().trim().toUpperCase(Locale.ROOT))));
        this.referencias = referencias;
    }

    public EscenarioSimulado escenario(String hostname) {
        return hostname == null ? EscenarioSimulado.ESTANDAR
                : escenarios.getOrDefault(hostname.toLowerCase(Locale.ROOT), EscenarioSimulado.ESTANDAR);
    }

    /**
     * Igual que el script real de la prueba de acceso (DEC-57): informa los permisos efectivos con la marca
     * KOMAINOS_ADMINISTRADOR, sin exigirlos, y solo falla si no conecta, no autentica o la elevación no funciona
     */
    @Override
    public ResultadoEjecucion probarConexion(SolicitudEjecucion s) {
        return switch (escenario(s.hostname())) {
            case SIN_CONEXION -> falloConexion(s);
            case SIN_AUTENTICACION -> falloAutenticacion();
            case ELEVACION_FALLIDA -> falloElevacion();
            case SIN_PERMISOS -> ResultadoEjecucion.exito(("Acceso verificado en %s (simulado): los scripts se "
                    + "ejecutarán como svc, sin privilegios de administrador\nKOMAINOS_ADMINISTRADOR=no")
                    .formatted(s.hostname()), List.of());
            default -> ResultadoEjecucion.exito(("Acceso verificado en %s (simulado): los scripts se ejecutarán "
                    + "como root, con privilegios de administrador\nKOMAINOS_ADMINISTRADOR=si")
                    .formatted(s.hostname()), List.of());
        };
    }

    private static ResultadoEjecucion falloElevacion() {
        return ResultadoEjecucion.fallo(TipoFallo.PERMISOS, "Incorrect sudo password (simulado)");
    }

    @Override
    public ResultadoEjecucion ejecutar(SolicitudEjecucion s) {
        ScriptPreparado script = PreparadorScript.preparar(s.familia(), s.contenido(), s.parametros(), s.listas());
        EscenarioSimulado escenario = escenario(s.hostname());
        if (escenario == EscenarioSimulado.SIN_CONEXION) {
            return falloConexion(s);
        }
        if (escenario == EscenarioSimulado.SIN_AUTENTICACION) {
            return falloAutenticacion();
        }
        if (escenario == EscenarioSimulado.ELEVACION_FALLIDA) {
            return falloElevacion();
        }
        if (s.esAnalisis()) {
            return ContratoAnalisis.aplicar(ResultadoEjecucion.exito(salidaAnalisis(s, escenario), List.of()), json);
        }
        if (escenario == EscenarioSimulado.SIN_PERMISOS) {
            return ResultadoEjecucion.fallo(TipoFallo.PERMISOS, "la cuenta no tiene privilegios de administrador (simulado)");
        }
        if (escenario == EscenarioSimulado.FALLO_TAREA) {
            return ResultadoEjecucion.codigoError(1, registroTarea(s, script, false),
                    "el script terminó con el código 1 (fallo simulado)");
        }
        return ResultadoEjecucion.exito(registroTarea(s, script, true), List.of());
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
        for (MetricaReferencia referencia : referencias.apply(s)) {
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
            return "Análisis simulado de %s (%s)%n%s".formatted(s.hostname(), nivel.name().toLowerCase(Locale.ROOT),
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
        return ResultadoEjecucion.fallo(TipoFallo.COMUNICACION,
                "%s (%s) no respondió por %s (simulado)".formatted(s.hostname(), s.direccionIp(), puerto));
    }

    private static ResultadoEjecucion falloAutenticacion() {
        return ResultadoEjecucion.fallo(TipoFallo.AUTENTICACION,
                "el servidor rechazó el usuario o la contraseña de la cuenta de servicio (simulado)");
    }
}
