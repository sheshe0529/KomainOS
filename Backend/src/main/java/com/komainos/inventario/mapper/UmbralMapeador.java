package com.komainos.inventario.mapper;

import com.komainos.inventario.dto.ExcepcionAplicadaRespuesta;
import com.komainos.inventario.dto.ExcepcionUmbralRespuesta;
import com.komainos.inventario.dto.ListaResumenRespuesta;
import com.komainos.inventario.dto.MetricaRespuesta;
import com.komainos.inventario.dto.PruebaUmbralRespuesta;
import com.komainos.inventario.dto.UmbralEnGrupoRespuesta;
import com.komainos.inventario.dto.UmbralRespuesta;
import com.komainos.inventario.dto.UmbralServidorRespuesta;
import com.komainos.inventario.dto.ValoresUmbralRespuesta;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralAplicado;
import com.komainos.inventario.model.UmbralExcepcion;
import com.komainos.inventario.service.ServicioUmbralesServidor.ResultadoPrueba;
import com.komainos.inventario.service.ServicioUmbralesServidor.UmbralDelServidor;
import com.komainos.shared.dto.ReferenciaSimple;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Espera las excepciones, sus objetivos y las listas ya cargados por el servicio: open-in-view está desactivado */
public final class UmbralMapeador {

    private UmbralMapeador() {
    }

    public static UmbralRespuesta resumen(Umbral u) {
        return umbral(u, null);
    }

    /** Excepciones por prioridad de su alcance (servidor, grupo, entorno, criticidad) y por nombre del objetivo */
    public static UmbralRespuesta detalle(Umbral u) {
        return umbral(u, u.getExcepciones().stream()
                .sorted(Comparator.comparing(UmbralExcepcion::alcance)
                        .thenComparing(e -> e.objetivo().nombre(), String.CASE_INSENSITIVE_ORDER))
                .map(UmbralMapeador::excepcion)
                .toList());
    }

    public static ExcepcionUmbralRespuesta excepcion(UmbralExcepcion e) {
        return new ExcepcionUmbralRespuesta(e.getId(), e.alcance(), objetivo(e), plano(e.getValorAdvertencia()),
                plano(e.getValorCritico()), ListaResumenRespuesta.de(e.getLista()), e.getMotivo(), e.isActivo(),
                e.getFechaActualizacion());
    }

    public static UmbralServidorRespuesta delServidor(UmbralDelServidor d) {
        UmbralAplicado a = d.aplicado();
        Umbral u = a.umbral();
        return new UmbralServidorRespuesta(new ReferenciaSimple(u.getId(), u.getNombre()), MetricaRespuesta.de(u.getMetrica()),
                a.origen(), excepcionAplicada(a), valores(a), valores(UmbralAplicado.global(u)),
                d.enGrupos().stream()
                        .map(g -> new UmbralEnGrupoRespuesta(new ReferenciaSimple(g.grupo().getId(), g.grupo().getNombre()),
                                excepcionAplicada(g.aplicado()), valores(g.aplicado())))
                        .toList());
    }

    public static PruebaUmbralRespuesta prueba(ResultadoPrueba r) {
        UmbralAplicado a = r.aplicado();
        boolean deLista = a.umbral().esDeLista();
        return new PruebaUmbralRespuesta(r.nivel(), a.origen(), excepcionAplicada(a), valores(a), plano(r.valor()),
                deLista ? r.faltantes() : null, deLista ? r.sobrantes() : null);
    }

    private static UmbralRespuesta umbral(Umbral u, List<ExcepcionUmbralRespuesta> excepciones) {
        int activas = (int) u.getExcepciones().stream().filter(UmbralExcepcion::isActivo).count();
        return new UmbralRespuesta(u.getId(), u.getNombre(), MetricaRespuesta.de(u.getMetrica()), u.isActivo(),
                u.getOperador(), plano(u.getValorAdvertencia()), plano(u.getValorCritico()),
                ListaResumenRespuesta.de(u.getLista()), u.getNivelFaltante(), u.getNivelSobrante(),
                u.getExcepciones().size(), activas, excepciones, u.getFechaActualizacion());
    }

    private static ValoresUmbralRespuesta valores(UmbralAplicado a) {
        return new ValoresUmbralRespuesta(a.operador(), plano(a.advertencia()), plano(a.critico()),
                ListaResumenRespuesta.de(a.lista()), a.nivelFaltante(), a.nivelSobrante());
    }

    private static ExcepcionAplicadaRespuesta excepcionAplicada(UmbralAplicado a) {
        UmbralExcepcion e = a.excepcion();
        return e == null ? null : new ExcepcionAplicadaRespuesta(e.getId(), e.alcance(), objetivo(e), e.getMotivo());
    }

    private static ReferenciaSimple objetivo(UmbralExcepcion e) {
        return new ReferenciaSimple(e.idObjetivo(), e.objetivo().nombre());
    }

    /** numeric(20,6) vuelve con seis decimales: 80.000000 se presenta como 80 */
    private static BigDecimal plano(BigDecimal valor) {
        return valor == null ? null : new BigDecimal(valor.stripTrailingZeros().toPlainString());
    }
}
