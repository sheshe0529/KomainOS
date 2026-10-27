package com.komainos.catalogo.mapper;

import com.komainos.catalogo.dto.ParametroPeticion;
import com.komainos.catalogo.dto.RevisionSalidaRespuesta;
import com.komainos.catalogo.dto.ScriptPreparadoRespuesta;
import com.komainos.catalogo.dto.ScriptResumenRespuesta;
import com.komainos.catalogo.dto.ScriptRespuesta;
import com.komainos.catalogo.dto.TareaRespuesta;
import com.komainos.catalogo.model.ImplementacionScript;
import com.komainos.catalogo.model.Script;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.DatosParametro;
import com.komainos.ejecucion.model.ScriptPreparado;
import com.komainos.ejecucion.service.PreparadorScript;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class CatalogoMapeador {

    private CatalogoMapeador() {
    }

    public static ScriptResumenRespuesta resumen(Script s) {
        return new ScriptResumenRespuesta(s.getId(), s.getNombre(), s.getTipo(), s.getDescripcion(), s.isActivo(),
                s.getImplementaciones().stream().map(CatalogoMapeador::resumen).toList());
    }

    public static ScriptRespuesta script(Script s, long tareasActivas) {
        return new ScriptRespuesta(s.getId(), s.getNombre(), s.getTipo(), s.getDescripcion(), s.isActivo(),
                tareasActivas, s.getImplementaciones().stream().map(CatalogoMapeador::implementacion).toList());
    }

    public static ScriptRespuesta.VersionRespuesta version(VersionScript v) {
        Integer vigente = v.getImplementacion().ultimaHabilitada().map(VersionScript::getId).orElse(null);
        return new ScriptRespuesta.VersionRespuesta(v.getId(), v.getNumeroVersion(), v.getContenido(), v.getNotasCambio(),
                v.isHabilitada(), v.getId().equals(vigente), v.getFechaCreacion(),
                v.getParametros().stream()
                        .map(p -> new ScriptRespuesta.ParametroRespuesta(p.getNombre(), p.getTipoDato(), p.isObligatorio(),
                                p.getValorPorDefecto()))
                        .toList());
    }

    public static ScriptPreparadoRespuesta preparado(ScriptPreparado s, List<String> problemas) {
        return new ScriptPreparadoRespuesta(s.nombreArchivo(), s.lenguaje(), s.contenido(), s.lineaInicioParametros(),
                s.lineaFinParametros(), problemas);
    }

    public static RevisionSalidaRespuesta revision(ServicioCatalogoAutomatizacion.RevisionSalida r) {
        return new RevisionSalidaRespuesta(r.valida(), r.errores(),
                r.metricas().stream()
                        .map(m -> new RevisionSalidaRespuesta.MetricaRevisada(m.codigo(), m.instancia(), m.valor(),
                                m.elementos(), m.reconocida(), m.nombre(), m.unidad()))
                        .toList(),
                r.faltantes());
    }

    public static TareaRespuesta tarea(Tarea t) {
        return new TareaRespuesta(t.getId(), t.getScript().getId(), t.getScript().getNombre(), t.getScript().isActivo(),
                t.getNombre(), t.getDescripcion(), t.getNivelAfectacion(), t.getPrioridad(),
                t.getDuracionEstimadaMinutos(), t.isActivo(), Set.copyOf(t.getIdsUmbrales()),
                ServicioCatalogoAutomatizacion.compatibilidad(t.getScript()));
    }

    public static List<DatosParametro> parametros(List<ParametroPeticion> peticiones) {
        return peticiones == null ? List.of() : peticiones.stream()
                .map(p -> new DatosParametro(p.nombre(), p.tipo(), p.obligatorio(), p.valorPorDefecto()))
                .toList();
    }

    private static ScriptResumenRespuesta.ImplementacionResumen resumen(ImplementacionScript i) {
        Instant ultima = i.getVersiones().stream().map(VersionScript::getFechaCreacion)
                .max(Instant::compareTo).orElse(null);
        return new ScriptResumenRespuesta.ImplementacionResumen(i.getId(), i.getVersionSistemaOperativo().getId(),
                i.getVersionSistemaOperativo().descripcionCompleta(), i.familia(),
                i.ultimaHabilitada().map(VersionScript::getNumeroVersion).orElse(null), i.getVersiones().size(), ultima);
    }

    private static ScriptRespuesta.ImplementacionRespuesta implementacion(ImplementacionScript i) {
        return new ScriptRespuesta.ImplementacionRespuesta(i.getId(), i.getVersionSistemaOperativo().getId(),
                i.getVersionSistemaOperativo().descripcionCompleta(), i.familia(), PreparadorScript.lenguaje(i.familia()),
                i.ultimaHabilitada().map(VersionScript::getNumeroVersion).orElse(null),
                i.getVersiones().stream().map(CatalogoMapeador::version).toList());
    }
}
