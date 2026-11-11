package com.komainos.ejecucion.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion.Conexion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuración de Ansible que resulta de una cuenta de servicio (DEC-57): las mismas variables que el adaptador escribe
 * en el inventario de cada ejecución, generadas con el mismo código, con los secretos enmascarados. Separa lo que aporta
 * la cuenta, lo que fija el controlador para todas las cuentas y lo que aporta el servidor donde se use.
 */
@Service
@RequiredArgsConstructor
public class ServicioVistaAnsible {

    public static final String SECRETO = "[protegida]";
    public static final String LLAVE = "[llave privada protegida]";
    public static final String SERVIDOR = "<IP del servidor>";

    private static final Set<String> DE_LA_CUENTA = Set.of("ansible_user", "ansible_password",
            "ansible_ssh_private_key_file", "ansible_become", "ansible_become_method", "ansible_become_user",
            "ansible_become_password");

    private final PropiedadesAutomatizacion propiedades;

    /**
     * Una vista por cada familia en la que la cuenta puede usarse: una llave SSH, sudo y su solo sirven en Linux y runas
     * solo en Windows; sin elevación y con contraseña, en ambas.
     *
     * @param familia si se indica, solo esa familia (vacío si la cuenta no sirve en ella)
     */
    public List<Vista> vistas(String usuario, boolean llave, MetodoElevacion metodo, boolean conContrasena,
                              FamiliaSistemaOperativo familia) {
        MetodoElevacion elevacion = metodo == null ? MetodoElevacion.NINGUNO : metodo;
        List<Vista> vistas = new ArrayList<>();
        for (FamiliaSistemaOperativo f : FamiliaSistemaOperativo.values()) {
            boolean admitida = (!llave || f == FamiliaSistemaOperativo.LINUX) && elevacion.admitidoEn(f);
            if (admitida && (familia == null || familia == f)) {
                vistas.add(vista(f, usuario, llave, elevacion, conContrasena));
            }
        }
        return vistas;
    }

    private Vista vista(FamiliaSistemaOperativo familia, String usuario, boolean llave, MetodoElevacion metodo,
                        boolean conContrasena) {
        String elevacion = conContrasena && metodo.admiteContrasena() ? SECRETO : null;
        Conexion conexion = new Conexion(usuario == null || usuario.isBlank() ? "<usuario>" : usuario.trim(), SECRETO,
                llave, metodo, elevacion);
        Map<String, Object> variables = ArtefactosAnsible.variables(SERVIDOR, familia, conexion, llave ? LLAVE : null,
                propiedades.ansible());
        List<Variable> cuenta = new ArrayList<>();
        List<Variable> controlador = new ArrayList<>();
        List<Variable> servidor = new ArrayList<>();
        variables.forEach((nombre, valor) -> {
            Variable variable = new Variable(nombre, String.valueOf(valor));
            if (nombre.equals("ansible_host")) {
                servidor.add(variable);
            } else if (DE_LA_CUENTA.contains(nombre)) {
                cuenta.add(variable);
            } else {
                controlador.add(variable);
            }
        });
        return new Vista(familia, List.copyOf(cuenta), List.copyOf(controlador), List.copyOf(servidor));
    }

    public record Variable(String nombre, String valor) {
    }

    public record Vista(FamiliaSistemaOperativo familia, List<Variable> cuenta, List<Variable> controlador,
                        List<Variable> servidor) {
    }
}
