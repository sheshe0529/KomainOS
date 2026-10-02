package com.komainos.credencial.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Credencial: versiones, revocación y mecanismos (RF04, HU04 CA3, DEC-39)")
class CredencialTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final SecretoCifrado SECRETO = new SecretoCifrado(new byte[]{1}, new byte[12], new byte[16], "AES-256-GCM");

    @Test
    @DisplayName("cada secreto nuevo es una versión más y la vigente es la de mayor número")
    void versiones() {
        CuentaServicio cuenta = CuentaServicio.nueva("svc-ansible", "ansible", null, AHORA);
        cuenta.agregarVersion(TipoAutenticacion.PASSWORD, null, SECRETO, null, AHORA);
        cuenta.agregarVersion(TipoAutenticacion.LLAVE_SSH, null, SECRETO, null, AHORA.plusSeconds(60));

        assertThat(cuenta.getVersiones()).hasSize(2);
        assertThat(cuenta.versionVigente()).get()
                .satisfies(v -> {
                    assertThat(v.getNumeroVersion()).isEqualTo(2);
                    assertThat(v.getTipoAutenticacion()).isEqualTo(TipoAutenticacion.LLAVE_SSH);
                    assertThat(v.tieneSu()).isFalse();
                });
    }

    @Test
    @DisplayName("DEC-39: la contraseña su acompaña solo al usuario Genérico")
    void contrasenaSu() {
        CuentaServicio cuenta = CuentaServicio.nueva("acceso", "appuser", null, AHORA);

        cuenta.agregarVersion(TipoAutenticacion.LLAVE_SSH, TipoUsuario.GENERICO, SECRETO, SECRETO, AHORA);
        assertThat(cuenta.versionVigente()).get().satisfies(v -> {
            assertThat(v.tieneSu()).isTrue();
            assertThat(v.secretoSu()).isPresent();
        });
        assertThatThrownBy(() -> cuenta.agregarVersion(TipoAutenticacion.PASSWORD, TipoUsuario.GENERICO, SECRETO, null, AHORA))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> cuenta.agregarVersion(TipoAutenticacion.PASSWORD, TipoUsuario.ADMINISTRADOR, SECRETO,
                SECRETO, AHORA)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("una credencial revocada conserva sus versiones pero no admite cambios")
    void revocada() {
        CuentaServicio cuenta = CuentaServicio.nueva("svc-ansible", "ansible", null, AHORA);
        cuenta.agregarVersion(TipoAutenticacion.PASSWORD, null, SECRETO, null, AHORA);
        cuenta.revocar(AHORA);

        assertThat(cuenta.getEstado()).isEqualTo(EstadoCredencial.REVOCADA);
        assertThat(cuenta.getFechaRevocacion()).isEqualTo(AHORA);
        assertThat(cuenta.getVersiones()).hasSize(1);
        assertThatThrownBy(() -> cuenta.agregarVersion(TipoAutenticacion.PASSWORD, null, SECRETO, null, AHORA))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> cuenta.actualizarDatos("otro", "otro", null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> cuenta.revocar(AHORA)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("HU04 CA3: WinRM solo admite contraseña, SSH admite contraseña o llave privada")
    void mecanismosPorFamilia() {
        assertThat(TipoAutenticacion.PASSWORD.admitidoEn(FamiliaSistemaOperativo.WINDOWS)).isTrue();
        assertThat(TipoAutenticacion.LLAVE_SSH.admitidoEn(FamiliaSistemaOperativo.WINDOWS)).isFalse();
        assertThat(TipoAutenticacion.LLAVE_SSH.admitidoEn(FamiliaSistemaOperativo.LINUX)).isTrue();
        assertThat(TipoAutenticacion.LLAVE_SSH.admitidoEn(null)).isTrue();
    }
}
