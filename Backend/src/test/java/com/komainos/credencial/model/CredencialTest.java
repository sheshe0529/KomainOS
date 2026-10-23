package com.komainos.credencial.model;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Credencial: secreto vigente, revocación y mecanismos (RF04, HU04 CA3, DEC-39, R2.4)")
class CredencialTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final SecretoCifrado SECRETO = new SecretoCifrado(new byte[]{1}, new byte[12], new byte[16], "AES-256-GCM");
    private static final SecretoCifrado OTRO = new SecretoCifrado(new byte[]{2}, new byte[12], new byte[16], "AES-256-GCM");

    @Test
    @DisplayName("rotar el secreto sobrescribe el vigente y actualiza su fecha, sin conservar el anterior")
    void rotacion() {
        CuentaServicio cuenta = CuentaServicio.nueva("svc-ansible", "ansible", null, AHORA);
        CredencialSecreto primero = cuenta.establecerSecreto(TipoAutenticacion.PASSWORD, null, SECRETO, null, AHORA);
        CredencialSecreto rotado = cuenta.establecerSecreto(TipoAutenticacion.LLAVE_SSH, null, OTRO, null,
                AHORA.plusSeconds(60));

        assertThat(rotado).isSameAs(primero);
        assertThat(cuenta.secretoVigente()).get().satisfies(s -> {
            assertThat(s.getTipoAutenticacion()).isEqualTo(TipoAutenticacion.LLAVE_SSH);
            assertThat(s.secreto().cifrado()).containsExactly(2);
            assertThat(s.getFechaActualizacion()).isEqualTo(AHORA.plusSeconds(60));
            assertThat(s.tieneSu()).isFalse();
        });
    }

    @Test
    @DisplayName("DEC-39: la contraseña su acompaña solo al usuario Genérico y se quita al pasar a Administrador")
    void contrasenaSu() {
        CuentaServicio cuenta = CuentaServicio.nueva("acceso", "appuser", null, AHORA);

        cuenta.establecerSecreto(TipoAutenticacion.LLAVE_SSH, TipoUsuario.GENERICO, SECRETO, SECRETO, AHORA);
        assertThat(cuenta.secretoVigente()).get().satisfies(s -> {
            assertThat(s.tieneSu()).isTrue();
            assertThat(s.secretoSu()).isPresent();
        });
        assertThatThrownBy(() -> cuenta.establecerSecreto(TipoAutenticacion.PASSWORD, TipoUsuario.GENERICO, SECRETO,
                null, AHORA)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> cuenta.establecerSecreto(TipoAutenticacion.PASSWORD, TipoUsuario.ADMINISTRADOR, SECRETO,
                SECRETO, AHORA)).isInstanceOf(ReglaNegocioException.class);

        cuenta.establecerSecreto(TipoAutenticacion.PASSWORD, TipoUsuario.ADMINISTRADOR, OTRO, null, AHORA);
        assertThat(cuenta.secretoVigente()).get().satisfies(s -> {
            assertThat(s.tieneSu()).isFalse();
            assertThat(s.secretoSu()).isEmpty();
        });
    }

    @Test
    @DisplayName("R2.4: revocar elimina el secreto y la credencial ya no admite cambios")
    void revocada() {
        CuentaServicio cuenta = CuentaServicio.nueva("svc-ansible", "ansible", null, AHORA);
        cuenta.establecerSecreto(TipoAutenticacion.PASSWORD, null, SECRETO, null, AHORA);
        cuenta.revocar(AHORA);

        assertThat(cuenta.getEstado()).isEqualTo(EstadoCredencial.REVOCADA);
        assertThat(cuenta.getFechaRevocacion()).isEqualTo(AHORA);
        assertThat(cuenta.secretoVigente()).isEmpty();
        assertThatThrownBy(() -> cuenta.establecerSecreto(TipoAutenticacion.PASSWORD, null, SECRETO, null, AHORA))
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
