package com.komainos.shared.util;

import com.komainos.shared.exception.ConflictoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Concurrencia optimista: la versión que leyó el formulario")
class ConcurrenciaTest {

    private static final Instant LEIDA = Instant.parse("2026-10-01T15:00:00.400Z");

    @Test
    @DisplayName("CP204: si el registro cambió después de abrir el formulario, responde conflicto con la fecha del cambio")
    void versionDesactualizada() {
        assertThatThrownBy(() -> Concurrencia.exigirVersion(LEIDA, LEIDA.plusSeconds(3), "el servidor SERVPRODAPPV01"))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Hubo otro cambio en el servidor SERVPRODAPPV01")
                // 15:00:03 UTC es 10:00:03 en Lima
                .hasMessageContaining("01/10/2026 10:00:03")
                .hasMessageContaining("Recargue");
    }

    @Test
    @DisplayName("la columna guarda segundos: la misma versión con otra fracción coincide, y sin versión no se compara")
    void mismaVersion() {
        assertThatCode(() -> Concurrencia.exigirVersion(LEIDA, Instant.parse("2026-10-01T15:00:00Z"), "el grupo Web"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Concurrencia.exigirVersion(null, LEIDA.plusSeconds(60), "el grupo Web"))
                .doesNotThrowAnyException();
    }
}
