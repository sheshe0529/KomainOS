package com.komainos.ejecucion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.FileSystems;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisplayName("Ejecutor remoto y adaptador Ansible del despliegue")
class EjecutorRemotoTest {

    private static final PropiedadesAutomatizacion PROPIEDADES = new PropiedadesAutomatizacion(false, 30000, 10,
            new PropiedadesAutomatizacion.Ansible("ansible-playbook", 30, new PropiedadesAutomatizacion.Ssh(22, true, null),
                    new PropiedadesAutomatizacion.Winrm(5986, "https", "ntlm", true)));

    private static SolicitudEjecucion tarea() {
        return SolicitudEjecucion.tarea(1, "SERVDESAUTV01", "192.0.2.10", FamiliaSistemaOperativo.LINUX, 7,
                "echo hola", List.of(), 5);
    }

    @Test
    @DisplayName("Convierte un error inesperado del adaptador en un resultado fallido sin exponer su mensaje")
    void errorInesperado() {
        PuertoAnsible roto = new PuertoAnsible() {
            @Override
            public ResultadoEjecucion ejecutar(SolicitudEjecucion solicitud) {
                throw new IllegalStateException("fallo interno");
            }

            @Override
            public ResultadoEjecucion probarConexion(SolicitudEjecucion solicitud) {
                throw new IllegalStateException("fallo interno");
            }
        };
        EjecutorRemoto ejecutor = new EjecutorRemoto(roto, PROPIEDADES);

        ResultadoEjecucion r = ejecutor.ejecutar(tarea());

        assertThat(r.correcto()).isFalse();
        assertThat(r.fallo()).isEqualTo(TipoFallo.CONFIGURACION);
        assertThat(r.error()).doesNotContain("fallo interno");
    }

    @Test
    @DisplayName("RF71: en un controlador sin POSIX (Windows) la operación falla por configuración sin descifrar la cuenta")
    void controladorIncompatible() {
        assumeFalse(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "Solo aplica fuera de Linux: en Linux el adaptador sí intenta ejecutar Ansible");
        PuertoConexionAutomatizacion sinDescifrar = (cuenta, familia, proposito) -> fail("No debe descifrar la cuenta");
        AnsibleReal adaptador = new AnsibleReal(sinDescifrar, new ObjectMapper(), PROPIEDADES);

        ResultadoEjecucion tarea = adaptador.ejecutar(tarea());
        ResultadoEjecucion acceso = adaptador.probarConexion(SolicitudEjecucion.conectividad(1, "SERVDESAUTV01",
                "192.0.2.10", FamiliaSistemaOperativo.LINUX, 7, 2));

        assertThat(tarea.fallo()).isEqualTo(TipoFallo.CONFIGURACION);
        assertThat(tarea.error()).contains("Linux");
        assertThat(acceso.fallo()).isEqualTo(TipoFallo.CONFIGURACION);
    }

    @ParameterizedTest
    @EnumSource(value = PropositoEjecucion.class, names = {"CONECTIVIDAD", "CONECTIVIDAD_EVALUACION"})
    @DisplayName("RF71: las comprobaciones de acceso llegan a Linux sin retornos de carro, aunque la imagen se construya en Windows")
    void comprobacionSinRetornosDeCarro(PropositoEjecucion proposito) throws Exception {
        String linux = AnsibleReal.comprobacion(proposito, FamiliaSistemaOperativo.LINUX);
        String windows = AnsibleReal.comprobacion(proposito, FamiliaSistemaOperativo.WINDOWS);

        assertThat(linux).startsWith("#!/usr/bin/env bash").doesNotContain("\r");
        assertThat(windows).isNotBlank().doesNotContain("\r");
    }
}
