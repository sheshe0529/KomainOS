package com.komainos.credencial.dto;

import com.komainos.credencial.config.PropiedadesCredenciales;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.model.TipoUsuario;
import com.komainos.credencial.service.ServicioCredenciales.AccesoExportado;
import com.komainos.credencial.service.ServicioCredenciales.Revelado;
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
        String su = "contrasena-su-de-prueba";
        TipoAutenticacion tipo = TipoAutenticacion.LLAVE_SSH;
        TipoUsuario usuario = TipoUsuario.GENERICO;
        CredencialPeticion alta = new CredencialPeticion("Prueba", "svc", null, tipo, usuario, llave, su);
        SecretoPeticion rotacion = new SecretoPeticion(tipo, usuario, llave, su);

        assertThat(List.of(alta, alta.aDatos(), rotacion, rotacion.aSecreto(),
                new Revelado(null, null, llave, su),
                new SecretoReveladoRespuesta(1, "Prueba", "svc", tipo, usuario, Instant.EPOCH, llave, su, 30),
                new AccesoExportado(tipo, usuario, "svc", llave, su),
                new CredencialArchivo(tipo.name(), usuario.name(), "svc", llave, su),
                new PropiedadesCredenciales(llave, 30), new ReautenticacionPeticion(su)))
                .extracting(Object::toString)
                .allSatisfy(texto -> assertThat(texto).doesNotContain(llave, su));
    }
}
