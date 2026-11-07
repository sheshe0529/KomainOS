package com.komainos.catalogo;

import com.komainos.catalogo.model.ParametroScript;
import com.komainos.catalogo.model.TipoParametro;
import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosParametro;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.repository.ListaReferenciaRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RF25/RF26: parámetros explícitos, listas por referencia y compatibilidad histórica")
class ParametrosScriptTest {
    private static final Instant AHORA = Instant.parse("2026-11-06T12:00:00Z");
    @Mock ListaReferenciaRepositorio listas;
    @InjectMocks ServicioCatalogoAutomatizacion servicio;

    @Test
    void escalarNecesitaValorAunqueAntesFueraOpcional() {
        var vista = servicio.vistaPrevia(FamiliaSistemaOperativo.LINUX, "echo $DIAS",
                List.of(new DatosParametro("DIAS", TipoParametro.INTEGER, null, null)));
        assertThat(vista.problemas()).anyMatch(p -> p.contains("DIAS necesita un valor"));
    }

    @Test
    void listaSeResuelveSinCopiarValoresYUsaElNombreDeclarado() {
        ListaReferencia lista = ListaReferencia.nueva("Archivos", TipoDato.STRING, false, AHORA);
        lista.reemplazarElementos(List.of("/etc/passwd:644", "/tmp/it's:600"), AHORA);
        when(listas.findById(7)).thenReturn(Optional.of(lista));

        var vista = servicio.vistaPrevia(FamiliaSistemaOperativo.LINUX, "printf '%s\\n' \"${ARCHIVOS[@]}\"",
                List.of(new DatosParametro("ARCHIVOS", TipoParametro.LISTA, null, 7)));
        assertThat(vista.problemas()).isEmpty();
        assertThat(vista.script().contenido()).contains("ARCHIVOS=('/etc/passwd:644' '/tmp/it'\"'\"'s:600')")
                .doesNotContain("KOMAINOS_LISTA_");
    }

    @Test
    void listaInactivaNoSePuedeAsignarEnUnaVersionNueva() {
        ListaReferencia lista = ListaReferencia.nueva("Retirada", TipoDato.INTEGER, false, AHORA);
        lista.desactivar();
        when(listas.findById(7)).thenReturn(Optional.of(lista));
        var vista = servicio.vistaPrevia(FamiliaSistemaOperativo.WINDOWS, "Write-Output $PUERTOS",
                List.of(new DatosParametro("PUERTOS", TipoParametro.LISTA, null, 7)));
        assertThat(vista.problemas()).anyMatch(p -> p.contains("inactiva"));
    }

    @Test
    void tiposNoAdmitenFuentesMezcladas() {
        var vista = servicio.vistaPrevia(FamiliaSistemaOperativo.LINUX, "echo",
                List.of(new DatosParametro("A", TipoParametro.STRING, "valor", 7),
                        new DatosParametro("B", TipoParametro.LISTA, "valor", null)));
        assertThat(vista.problemas()).anyMatch(p -> p.contains("escalar A no admite una lista"))
                .anyMatch(p -> p.contains("lista B no admite un valor escalar"));
    }

    @Test
    void unEscalarHistoricoVacioConservaSuSemanticaDeEjecucion() {
        VersionScript version = VersionScript.nueva(null, 1, "echo", null, AHORA);
        version.agregarParametro(ParametroScript.nuevo(version, "VACIO", TipoParametro.STRING, null, null, 1));
        assertThat(ServicioCatalogoAutomatizacion.valoresDeEjecucion(version).getFirst().valor()).isNull();
        assertThat(version.usaListasImplicitas()).isFalse();
    }
}
