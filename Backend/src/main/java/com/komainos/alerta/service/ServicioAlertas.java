package com.komainos.alerta.service;

import com.komainos.alerta.model.Alerta;
import com.komainos.alerta.model.AlertaDestinatario;
import com.komainos.alerta.model.TipoAlerta;
import com.komainos.alerta.repository.AlertaDestinatarioRepositorio;
import com.komainos.alerta.repository.AlertaRepositorio;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Alertas dirigidas a cada usuario (RF69, HU30): cada destinatario ve y lee solo las suyas */
@Service
@RequiredArgsConstructor
public class ServicioAlertas {

    private final AlertaRepositorio alertas;
    private final AlertaDestinatarioRepositorio destinatarios;
    private final Clock reloj;

    /** Sin destinatarios no se registra nada: una alerta que nadie puede ver no aporta */
    @Transactional
    public void emitir(DatosAlerta datos, Collection<Integer> usuarios) {
        Set<Integer> unicos = new LinkedHashSet<>(usuarios.stream().filter(Objects::nonNull).toList());
        if (unicos.isEmpty()) {
            return;
        }
        Alerta alerta = alertas.save(Alerta.nueva(datos.tipo(), datos.titulo(), datos.mensaje(), datos.idServidor(),
                datos.idGrupo(), datos.idOrden(), datos.idIncidencia(), Tiempo.ahora(reloj)));
        unicos.forEach(u -> destinatarios.save(AlertaDestinatario.nuevo(alerta, u)));
    }

    @Transactional(readOnly = true)
    public Page<AlertaDestinatario> listar(Integer idUsuario, boolean soloNoLeidas, Pageable pagina) {
        return destinatarios.deUsuario(idUsuario, soloNoLeidas, pagina);
    }

    @Transactional(readOnly = true)
    public long pendientes(Integer idUsuario) {
        return destinatarios.countByIdUsuarioAndLeidaFalse(idUsuario);
    }

    /** Una alerta ajena responde como inexistente (404): no confirma que exista */
    @Transactional(readOnly = true)
    public AlertaDestinatario obtener(Integer id, Integer idUsuario) {
        return destinatarios.findByIdAndIdUsuario(id, idUsuario)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la alerta", id));
    }

    @Transactional
    public void leer(Integer id, Integer idUsuario) {
        obtener(id, idUsuario).leer(Tiempo.ahora(reloj));
    }

    @Transactional
    public int leerTodas(Integer idUsuario) {
        return destinatarios.leerTodas(idUsuario, Tiempo.ahora(reloj));
    }

    /** Qué ocurrió y a qué se refiere; los identificadores nulos no aplican */
    public record DatosAlerta(TipoAlerta tipo, String titulo, String mensaje, Integer idServidor, Integer idGrupo,
                              Integer idOrden, Integer idIncidencia) {

        public static DatosAlerta de(TipoAlerta tipo, String titulo, String mensaje) {
            return new DatosAlerta(tipo, titulo, mensaje, null, null, null, null);
        }

        public DatosAlerta servidor(Integer id) {
            return new DatosAlerta(tipo, titulo, mensaje, id, idGrupo, idOrden, idIncidencia);
        }

        public DatosAlerta grupo(Integer id) {
            return new DatosAlerta(tipo, titulo, mensaje, idServidor, id, idOrden, idIncidencia);
        }

        public DatosAlerta orden(Integer id) {
            return new DatosAlerta(tipo, titulo, mensaje, idServidor, idGrupo, id, idIncidencia);
        }

        public DatosAlerta incidencia(Integer id) {
            return new DatosAlerta(tipo, titulo, mensaje, idServidor, idGrupo, idOrden, id);
        }
    }
}
