package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Tipo de dato: forma canónica de los elementos y límites de umbral (RF16, R2.4)")
class TipoDatoTest {

    @ParameterizedTest(name = "{0} «{1}» se guarda como «{3}»")
    @CsvSource(delimiter = '|', value = {
            "INTEGER | 0443   | false | 443",
            "INTEGER | -0     | false | 0",
            "INTEGER | +22    | false | 22",
            "DECIMAL | 1.50   | false | 1.5",
            "DECIMAL | 10.000 | false | 10",
            "STRING  | ' sshd '  | false | sshd",
            "STRING  | SSHD   | true  | sshd",
            "STRING  | SSHD   | false | SSHD",
            "STRING  | Windows Update | false | Windows Update"
    })
    @DisplayName("CP187: cada tipo tiene una sola forma canónica")
    void canonico(TipoDato tipo, String crudo, boolean ignoraMayusculas, String esperado) {
        assertThat(tipo.canonico(crudo, ignoraMayusculas)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CP189: elementos que no corresponden al tipo se rechazan con un mensaje en español")
    void invalidos() {
        assertThatThrownBy(() -> TipoDato.INTEGER.canonico("http", false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es un número entero");
        assertThatThrownBy(() -> TipoDato.DECIMAL.canonico("1.2.3", false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es un número");
        assertThatThrownBy(() -> TipoDato.STRING.canonico("crond,sshd", false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("comas");
        assertThatThrownBy(() -> TipoDato.STRING.canonico("   ", false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("vacíos");
        assertThatThrownBy(() -> TipoDato.STRING.canonico("x".repeat(256), false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("255");
        assertThatThrownBy(() -> TipoDato.BOOLEAN.canonico("true", false))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("los números se ordenan por su valor y el texto alfabéticamente")
    void orden() {
        List<String> puertos = new ArrayList<>(List.of("443", "22", "8080", "80"));
        puertos.sort(TipoDato.INTEGER.orden());
        assertThat(puertos).containsExactly("22", "80", "443", "8080");
        List<String> servicios = new ArrayList<>(List.of("sshd", "crond", "rsyslog"));
        servicios.sort(TipoDato.STRING.orden());
        assertThat(servicios).containsExactly("crond", "rsyslog", "sshd");
    }

    @Test
    @DisplayName("el límite de un umbral respeta numeric(20,6) y el tipo de la métrica")
    void limites() {
        TipoDato.DECIMAL.validarLimite(new BigDecimal("99.999999"), "crítico");
        TipoDato.INTEGER.validarLimite(new BigDecimal("4.000"), "crítico");
        TipoDato.BOOLEAN.validarLimite(BigDecimal.ONE, "crítico");
        assertThatThrownBy(() -> TipoDato.INTEGER.validarLimite(new BigDecimal("4.5"), "crítico"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entera");
        assertThatThrownBy(() -> TipoDato.BOOLEAN.validarLimite(new BigDecimal("2"), "crítico"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("0 o 1");
        assertThatThrownBy(() -> TipoDato.DECIMAL.validarLimite(new BigDecimal("0.1234567"), "crítico"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("6 decimales");
        assertThatThrownBy(() -> TipoDato.DECIMAL.validarLimite(new BigDecimal("1E15"), "crítico"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("demasiado grande");
    }

    @ParameterizedTest(name = "{0}: medición {1} frente a {2} → {3}")
    @CsvSource({
            "MAYOR, 85, 80, true", "MAYOR, 80, 80, false",
            "MAYOR_IGUAL, 80, 80, true", "MENOR, 9, 10, true",
            "MENOR_IGUAL, 10, 10, true", "MENOR_IGUAL, 11, 10, false",
            "IGUAL, 0, 0, true", "DISTINTO, 1, 0, true", "DISTINTO, 0, 0, false"
    })
    @DisplayName("cada operador compara la medición con el límite")
    void operadores(OperadorComparacion operador, BigDecimal medicion, BigDecimal limite, boolean esperado) {
        assertThat(operador.seCumple(medicion, limite)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CP045/CP046: con mayor el crítico no es menor que la advertencia, con menor no es mayor")
    void coherencia() {
        assertThat(OperadorComparacion.MAYOR_IGUAL.esCoherente(new BigDecimal("80"), new BigDecimal("90"))).isTrue();
        assertThat(OperadorComparacion.MAYOR_IGUAL.esCoherente(new BigDecimal("90"), new BigDecimal("80"))).isFalse();
        assertThat(OperadorComparacion.MENOR_IGUAL.esCoherente(new BigDecimal("20"), new BigDecimal("10"))).isTrue();
        assertThat(OperadorComparacion.MENOR.esCoherente(new BigDecimal("10"), new BigDecimal("20"))).isFalse();
        assertThat(OperadorComparacion.DISTINTO.esCoherente(new BigDecimal("10"), new BigDecimal("20"))).isTrue();
        assertThat(OperadorComparacion.MAYOR.esCoherente(null, new BigDecimal("20"))).isTrue();
    }

    @Test
    @DisplayName("Un exponente enorme se rechaza sin construir su forma plana, y un texto largo antes de convertirlo")
    void expansionAcotada() {
        long inicio = System.nanoTime();
        assertThatThrownBy(() -> TipoDato.DECIMAL.canonico("1e100000", false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("255 caracteres o menos");
        assertThatThrownBy(() -> TipoDato.DECIMAL.canonico("-1e-300", false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("255 caracteres o menos");
        assertThatThrownBy(() -> TipoDato.INTEGER.canonico("9".repeat(256), false))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("supera los 255 caracteres");
        // Sin materializar cien mil dígitos la validación es inmediata
        assertThat(System.nanoTime() - inicio).isLessThan(1_000_000_000L);

        assertThat(TipoDato.DECIMAL.canonico("1e3", false)).isEqualTo("1000");
        assertThat(TipoDato.DECIMAL.canonico("1.5e-3", false)).isEqualTo("0.0015");
        assertThat(TipoDato.DECIMAL.canonico("9".repeat(255), false)).hasSize(255);
    }
}
