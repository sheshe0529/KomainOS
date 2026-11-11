package com.komainos.ejecucion.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.ejecucion.service.ServicioVistaAnsible.Variable;
import com.komainos.ejecucion.service.ServicioVistaAnsible.Vista;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Vista de la configuración de Ansible de una cuenta (DEC-57)")
class ServicioVistaAnsibleTest {

    private final ServicioVistaAnsible servicio = new ServicioVistaAnsible(new PropiedadesAutomatizacion(false, 30000, 10,
            new PropiedadesAutomatizacion.Ansible("ansible-playbook", 30,
                    new PropiedadesAutomatizacion.Ssh(22, true, null),
                    new PropiedadesAutomatizacion.Winrm(5986, "https", "ntlm", true))));

    @Test
    @DisplayName("su con llave: solo Linux, con las variables de elevación de la cuenta y los secretos enmascarados")
    void suConLlave() {
        List<Vista> vistas = servicio.vistas("deploy", true, MetodoElevacion.SU, true, null);

        assertThat(vistas).extracting(Vista::familia).containsExactly(FamiliaSistemaOperativo.LINUX);
        Vista v = vistas.getFirst();
        assertThat(v.cuenta()).extracting(Variable::nombre).containsExactly("ansible_user",
                "ansible_ssh_private_key_file", "ansible_become", "ansible_become_method", "ansible_become_user",
                "ansible_become_password");
        assertThat(v.cuenta()).contains(new Variable("ansible_become_method", "su"),
                new Variable("ansible_become_user", "root"),
                new Variable("ansible_become_password", ServicioVistaAnsible.SECRETO),
                new Variable("ansible_ssh_private_key_file", ServicioVistaAnsible.LLAVE));
        assertThat(v.servidor()).containsExactly(new Variable("ansible_host", ServicioVistaAnsible.SERVIDOR));
        assertThat(v.controlador()).extracting(Variable::nombre).contains("ansible_connection", "ansible_port",
                "ansible_ssh_common_args");
    }

    @Test
    @DisplayName("Sin elevación y con contraseña sirve en ambas familias; runas solo en Windows")
    void familias() {
        assertThat(servicio.vistas("svc", false, MetodoElevacion.NINGUNO, false, null)).extracting(Vista::familia)
                .containsExactly(FamiliaSistemaOperativo.LINUX, FamiliaSistemaOperativo.WINDOWS);
        assertThat(servicio.vistas("svc", false, MetodoElevacion.RUNAS, false, null)).extracting(Vista::familia)
                .containsExactly(FamiliaSistemaOperativo.WINDOWS);
        assertThat(servicio.vistas("svc", true, MetodoElevacion.RUNAS, false, null)).isEmpty();
        assertThat(servicio.vistas("svc", false, MetodoElevacion.SUDO, false, FamiliaSistemaOperativo.WINDOWS)).isEmpty();
    }
}
