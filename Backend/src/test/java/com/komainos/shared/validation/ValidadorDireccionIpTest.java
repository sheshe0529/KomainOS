package com.komainos.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Dirección IP: validación y forma canónica (RF10, RF12)")
class ValidadorDireccionIpTest {

    @ParameterizedTest(name = "«{0}» se guarda como «{1}»")
    @CsvSource(delimiter = '|', value = {
            "2001:0DB8:0000:0000:0000:0000:0000:0001 | 2001:db8::1",
            "2001:db8:0:0:0:0:0:1                    | 2001:db8::1",
            "' 2001:DB8::1 '                         | 2001:db8::1",
            "2001:db8:0:0:1:0:0:1                    | 2001:db8::1:0:0:1",
            "2001:db8:0:1:1:1:1:1                    | 2001:db8:0:1:1:1:1:1",
            "fe80:0:0:0:0:0:0:0                      | fe80::",
            "0:0:0:0:0:0:0:1                         | ::1",
            "10.0.0.1                                | 10.0.0.1"
    })
    @DisplayName("CP210: una IPv6 se escribe según RFC 5952, así dos notaciones de la misma dirección son un duplicado")
    void canonica(String valor, String esperado) {
        assertThat(ValidadorDireccionIp.canonica(valor)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("lo inválido vuelve recortado para que la validación lo informe, y nunca se consulta un DNS")
    void invalidas() {
        assertThat(ValidadorDireccionIp.canonica(" servidor.local ")).isEqualTo("servidor.local");
        assertThat(ValidadorDireccionIp.esValida("servidor.local")).isFalse();
        assertThat(ValidadorDireccionIp.esValida("2001:db8::g")).isFalse();
        assertThat(ValidadorDireccionIp.esValida("256.0.0.1")).isFalse();
        assertThat(ValidadorDireccionIp.canonica(null)).isNull();
    }
}
