package com.komainos.seguridad.service;

import com.komainos.seguridad.config.CodificadorContrasena;
import com.komainos.seguridad.config.PropiedadesJwt;
import com.komainos.seguridad.service.ServicioJwt.TokenVerificado;
import com.komainos.shared.exception.DemasiadosIntentosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Sesiones: contraseñas, intentos fallidos y revocación de tokens (RNF01, RNF02)")
class SesionesTest {

    /** Reloj que avanza solo cuando la prueba lo pide */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-10-01T15:00:00Z");

        void avanzar(Duration lapso) {
            ahora = ahora.plus(lapso);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }

    @Nested
    @DisplayName("contraseñas")
    class Contrasenas {

        private final CodificadorContrasena codificador = new CodificadorContrasena();

        @Test
        @DisplayName("BCrypt solo usa 72 bytes: una contraseña más larga no se guarda ni se acepta (CVE-2025-22228)")
        void limiteDeBytes() {
            String limite = "a".repeat(72);
            String hash = codificador.encode(limite);

            assertThat(codificador.matches(limite, hash)).isTrue();
            // Con BCrypt sin el límite, cualquier sufijo después del byte 72 coincidiría con el hash
            assertThat(codificador.matches(limite + "cualquier-cosa", hash)).isFalse();
            assertThatThrownBy(() -> codificador.encode(limite + "b")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("el límite cuenta bytes UTF-8: cada ñ o vocal con tilde ocupa dos")
        void bytesYNoCaracteres() {
            assertThat(CodificadorContrasena.excede("ñ".repeat(36))).isFalse();
            assertThat(CodificadorContrasena.excede("ñ".repeat(37))).isTrue();
            assertThat(CodificadorContrasena.excede(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("intentos fallidos")
    class Intentos {

        private final RelojManual reloj = new RelojManual();
        private final LimitadorIntentos limitador = new LimitadorIntentos(reloj);

        @Test
        @DisplayName("CP205: al quinto fallo la cuenta espera un minuto, y la espera se duplica hasta 15 minutos")
        void bloqueoProgresivo() {
            for (int i = 0; i < LimitadorIntentos.FALLOS_POR_CUENTA - 1; i++) {
                limitador.registrarFallo("login:admin|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            }
            assertThatCode(() -> limitador.exigirPermitido("login:admin|10.0.0.9")).doesNotThrowAnyException();

            limitador.registrarFallo("login:admin|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            assertThatThrownBy(() -> limitador.exigirPermitido("login:admin|10.0.0.9"))
                    .isInstanceOf(DemasiadosIntentosException.class)
                    .hasMessageContaining("un minuto")
                    .satisfies(ex -> assertThat(((DemasiadosIntentosException) ex).getSegundosEspera()).isEqualTo(60));

            reloj.avanzar(Duration.ofMinutes(1));
            limitador.registrarFallo("login:admin|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            assertThatThrownBy(() -> limitador.exigirPermitido("login:admin|10.0.0.9"))
                    .satisfies(ex -> assertThat(((DemasiadosIntentosException) ex).getSegundosEspera()).isEqualTo(120));

            for (int i = 0; i < 10; i++) {
                limitador.registrarFallo("login:admin|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            }
            assertThatThrownBy(() -> limitador.exigirPermitido("login:admin|10.0.0.9"))
                    .satisfies(ex -> assertThat(((DemasiadosIntentosException) ex).getSegundosEspera())
                            .isEqualTo(LimitadorIntentos.ESPERA_MAXIMA.toSeconds()));
        }

        @Test
        @DisplayName("un acceso correcto reinicia el conteo, y cada clave se cuenta por separado")
        void exitoYClavesIndependientes() {
            for (int i = 0; i < LimitadorIntentos.FALLOS_POR_CUENTA - 1; i++) {
                limitador.registrarFallo("login:admin|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            }
            limitador.registrarExito("login:admin|10.0.0.9");
            limitador.registrarFallo("login:admin|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            assertThatCode(() -> limitador.exigirPermitido("login:admin|10.0.0.9")).doesNotThrowAnyException();

            for (int i = 0; i < LimitadorIntentos.FALLOS_POR_CUENTA; i++) {
                limitador.registrarFallo("login:operador|10.0.0.9", LimitadorIntentos.FALLOS_POR_CUENTA);
            }
            assertThatThrownBy(() -> limitador.exigirPermitido("login:operador|10.0.0.9"))
                    .isInstanceOf(DemasiadosIntentosException.class);
            assertThatCode(() -> limitador.exigirPermitido("login:admin|10.0.0.9")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("vencida la espera vuelve a admitir intentos")
        void esperaVencida() {
            for (int i = 0; i < LimitadorIntentos.FALLOS_POR_CUENTA; i++) {
                limitador.registrarFallo("reautenticacion:7", LimitadorIntentos.FALLOS_POR_CUENTA);
            }
            reloj.avanzar(LimitadorIntentos.ESPERA_INICIAL);
            assertThatCode(() -> limitador.exigirPermitido("reautenticacion:7")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("revocación de tokens")
    class Revocacion {

        private final RegistroRevocaciones registro = new RegistroRevocaciones();
        private final ServicioJwt jwt = new ServicioJwt(new PropiedadesJwt(
                Base64.getEncoder().encodeToString("clave-de-prueba-de-32-bytes-o-mas!!".getBytes()), 60), registro);

        @Test
        @DisplayName("CP206: un token cerrado deja de valer aunque no haya vencido; los demás siguen vigentes")
        void cierreDeSesion() {
            String cerrado = jwt.generar("admin", "ADMINISTRADOR", 60);
            String otro = jwt.generar("admin", "ADMINISTRADOR", 60);
            TokenVerificado verificado = jwt.verificar(cerrado).orElseThrow();

            jwt.revocar(verificado);

            assertThat(jwt.verificar(cerrado)).isEmpty();
            assertThat(jwt.codigoUsuarioSiEsValido(otro)).contains("admin");
        }

        @Test
        @DisplayName("revocar las sesiones de una cuenta invalida los tokens ya emitidos, sin importar mayúsculas")
        void sesionesDeLaCuenta() {
            String anterior = jwt.generar("Operador", "OPERADOR", 60);

            registro.revocarSesionesDe("operador");

            assertThat(jwt.verificar(anterior)).isEmpty();
            assertThat(registro.estaRevocado("otro-id", "OPERADOR", Instant.now().plusSeconds(5))).isFalse();
        }

        @Test
        @DisplayName("un token alterado o firmado con otra llave no se acepta")
        void firmaInvalida() {
            String token = jwt.generar("admin", "ADMINISTRADOR", 60);
            ServicioJwt otraLlave = new ServicioJwt(new PropiedadesJwt(
                    Base64.getEncoder().encodeToString("otra-clave-de-prueba-de-32-bytes!!".getBytes()), 60), registro);

            assertThat(otraLlave.verificar(token)).isEmpty();
            assertThat(jwt.verificar(token.substring(0, token.length() - 2) + "xx")).isEmpty();
            assertThat(jwt.verificar("no-es-un-token")).isEmpty();
        }
    }
}
