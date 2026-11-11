package com.komainos.credencial.dto;

import com.komainos.ejecucion.service.ServicioVistaAnsible.Variable;
import com.komainos.ejecucion.service.ServicioVistaAnsible.Vista;
import com.komainos.inventario.model.FamiliaSistemaOperativo;

import java.util.List;

/**
 * Variables de Ansible de una cuenta en una familia, con los secretos enmascarados: las de la cuenta, las que fija el
 * controlador para todas y la del servidor donde se use (DEC-57)
 */
public record VistaAnsibleRespuesta(FamiliaSistemaOperativo familia, List<VariableAnsibleRespuesta> cuenta,
                                    List<VariableAnsibleRespuesta> controlador, List<VariableAnsibleRespuesta> servidor) {

    public record VariableAnsibleRespuesta(String nombre, String valor) {
    }

    public static VistaAnsibleRespuesta de(Vista v) {
        return new VistaAnsibleRespuesta(v.familia(), variables(v.cuenta()), variables(v.controlador()),
                variables(v.servidor()));
    }

    private static List<VariableAnsibleRespuesta> variables(List<Variable> variables) {
        return variables.stream().map(x -> new VariableAnsibleRespuesta(x.nombre(), x.valor())).toList();
    }
}
