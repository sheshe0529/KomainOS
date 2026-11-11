package com.komainos.credencial.dto;

import com.komainos.credencial.config.PropiedadesCredenciales;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.service.ServicioCredenciales.AccesoExportado;
import com.komainos.credencial.service.ServicioCredenciales.Revelado;
import com.komainos.ejecucion.model.MetodoElevacion;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion.Conexion;
import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.seguridad.dto.ReautenticacionPeticion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretosTextoTest {

    @Test
    void laRepresentacionDeLasCredencialesOcultaLosSecretos() {
        String llave = "llave-privada-de-prueba";
        String elevacion = "contrasena-de-elevacion-de-prueba";
        TipoAutenticacion tipo = TipoAutenticacion.LLAVE_SSH;
        MetodoElevacion metodo = MetodoElevacion.SU;
        CredencialPeticion alta = new CredencialPeticion("Prueba", "svc", null, tipo, metodo, llave, true, elevacion);
        SecretoPeticion rotacion = new SecretoPeticion(tipo, metodo, llave, true, elevacion);

        assertThat(List.of(alta, alta.aDatos(), rotacion, rotacion.aSecreto(),
                new Revelado(null, null, llave, elevacion),
                new SecretoReveladoRespuesta(1, "Prueba", "svc", tipo, metodo, Instant.EPOCH, llave, elevacion, 30),
                new AccesoExportado(tipo, metodo, "svc", llave, elevacion),
                new CredencialArchivo(tipo.name(), metodo.name(), "svc", llave, elevacion),
                new Conexion("svc", llave, true, metodo, elevacion),
                new PropiedadesCredenciales(llave, 30), new ReautenticacionPeticion(elevacion)))
                .extracting(Object::toString)
                .allSatisfy(texto -> assertThat(texto).doesNotContain(llave, elevacion));
    }
}
