package com.komainos.inventario.model;

import com.komainos.inventario.model.ListaReferencia.CambioElementos;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Lista de referencia: elementos canónicos y su vigencia (RF16, R2.4)")
class ListaReferenciaTest {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00.123456Z");
    private static final Instant T1 = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-03T12:00:00Z");

    private static ListaReferencia puertos(String... elementos) {
        ListaReferencia lista = ListaReferencia.nueva("Puertos web", TipoDato.INTEGER, false, T0);
        lista.reemplazarElementos(List.of(elementos), T0);
        return lista;
    }

    @Test
    @DisplayName("CP187: los elementos se guardan canónicos, sin repetir y en orden")
    void canonicos() {
        ListaReferencia lista = puertos("443", "0080", "80", " 22 ");

        assertThat(lista.elementosVigentes()).containsExactly("22", "80", "443");
        assertThat(lista.getItems()).hasSize(3).allMatch(i -> i.getFechaAlta().equals(T0) && i.estaVigente());
    }

    @Test
    @DisplayName("CP188: modificar la lista retira con fecha de baja y agrega con fecha de alta, sin tocar los que siguen")
    void historial() {
        ListaReferencia lista = puertos("22", "80", "443");

        CambioElementos cambio = lista.reemplazarElementos(List.of("22", "443", "8443"), T1);

        assertThat(cambio.agregados()).containsExactly("8443");
        assertThat(cambio.retirados()).containsExactly("80");
        assertThat(lista.elementosVigentes()).containsExactly("22", "443", "8443");
        assertThat(lista.getItems()).hasSize(4);
        ListaReferenciaItem retirado = lista.getItems().stream().filter(i -> i.getValor().equals("80")).findFirst().orElseThrow();
        assertThat(retirado.getFechaBaja()).isEqualTo(T1);
        // R2.4: el contenido pasado se reconstruye con las fechas, para explicar una evaluación anterior
        assertThat(lista.elementosEn(T0)).containsExactly("22", "80", "443");
        assertThat(lista.elementosEn(T1)).containsExactly("22", "443", "8443");
        assertThat(lista.getFechaActualizacion()).isEqualTo(T1);
    }

    @Test
    @DisplayName("CP188: un elemento retirado vuelve con un periodo nuevo y la clave no se repite aunque el reloj no avance")
    void reincorporacion() {
        ListaReferencia lista = puertos("22", "80");

        lista.reemplazarElementos(List.of("22"), T0);
        lista.reemplazarElementos(List.of("22", "80"), T0);

        List<ListaReferenciaItem> del80 = lista.getItems().stream().filter(i -> i.getValor().equals("80")).toList();
        assertThat(del80).hasSize(2);
        ListaReferenciaItem primero = del80.get(0);
        ListaReferenciaItem segundo = del80.get(1);
        // ck_lista_referencia_item_vigencia: baja posterior al alta; la nueva alta no se superpone ni repite la clave
        assertThat(primero.getFechaBaja()).isAfter(primero.getFechaAlta());
        assertThat(segundo.getFechaAlta()).isEqualTo(primero.getFechaBaja());
        assertThat(segundo.getClave()).isNotEqualTo(primero.getClave());
        assertThat(lista.elementosVigentes()).containsExactly("22", "80");
    }

    @Test
    @DisplayName("sin cambios no se toca ningún elemento ni la fecha de actualización")
    void sinCambios() {
        ListaReferencia lista = puertos("22", "80");

        CambioElementos cambio = lista.reemplazarElementos(List.of("80", "022"), T2);

        assertThat(cambio.hayCambios()).isFalse();
        assertThat(lista.getItems()).hasSize(2);
        // fecha_actualizacion es TIMESTAMP(0): se guarda en segundos
        assertThat(lista.getFechaActualizacion()).isEqualTo(T0.truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("CP187: una lista que no distingue mayúsculas guarda en minúsculas y une los repetidos al activarlo")
    void mayusculas() {
        ListaReferencia lista = ListaReferencia.nueva("Servicios base", TipoDato.STRING, false, T0);
        lista.reemplazarElementos(List.of("SSHD", "crond"), T0);
        assertThat(lista.elementosVigentes()).containsExactly("SSHD", "crond");

        lista.definirIgnoraMayusculas(true);
        CambioElementos cambio = lista.reemplazarElementos(List.of("sshd", "SSHD", "Crond"), T1);

        assertThat(lista.elementosVigentes()).containsExactly("crond", "sshd");
        assertThat(cambio.retirados()).containsExactly("SSHD");
        assertThat(cambio.agregados()).containsExactly("sshd");
        assertThat(lista.canonico(" RSYSLOG ")).isEqualTo("rsyslog");
    }

    @Test
    @DisplayName("ignorar mayúsculas solo aplica a listas de texto")
    void mayusculasSoloTexto() {
        ListaReferencia lista = ListaReferencia.nueva("Puertos", TipoDato.INTEGER, true, T0);
        assertThat(lista.isIgnoraMayusculas()).isFalse();
    }

    @Test
    @DisplayName("CP189: una lista no queda vacía ni supera los 500 elementos, y no admite booleanos")
    void limites() {
        ListaReferencia lista = puertos("22");
        assertThatThrownBy(() -> lista.reemplazarElementos(Collections.emptyList(), T1))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("al menos un elemento");
        List<String> muchos = IntStream.rangeClosed(1, 501).mapToObj(String::valueOf).toList();
        assertThatThrownBy(() -> lista.reemplazarElementos(muchos, T1))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("500");
        assertThatThrownBy(() -> lista.reemplazarElementos(List.of("22", "http"), T1))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("http");
        // Un rechazo no deja cambios a medias
        assertThat(lista.elementosVigentes()).containsExactly("22");
        assertThatThrownBy(() -> ListaReferencia.nueva("Banderas", TipoDato.BOOLEAN, false, T0))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("activar o desactivar dos veces seguidas se rechaza")
    void estado() {
        ListaReferencia lista = puertos("22");
        lista.desactivar();
        assertThat(lista.isActivo()).isFalse();
        assertThatThrownBy(lista::desactivar).isInstanceOf(ReglaNegocioException.class);
        lista.activar();
        assertThatThrownBy(lista::activar).isInstanceOf(ReglaNegocioException.class);
    }
}
