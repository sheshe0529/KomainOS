package com.komainos.auditoria.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.auditoria.model.RegistroAuditoria;
import com.komainos.auditoria.repository.AuditoriaRepositorio;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Se une a la transacción del llamador: el cambio y su registro de auditoría se aplican juntos o ninguno (RNF06) */
@Service
@RequiredArgsConstructor
public class ServicioAuditoria {

    private final AuditoriaRepositorio repositorio;
    private final ObjectMapper mapeadorJson;
    private final Clock reloj;

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Actor actor, Operacion operacion) {
        repositorio.save(RegistroAuditoria.builder()
                .idUsuario(actor.usuarioId())
                .proceso(actor.proceso())
                .idServidor(operacion.idServidor())
                .idGrupoMantenimiento(operacion.idGrupo())
                .idOrden(operacion.idOrden())
                .operacion(operacion.nombre())
                .entidad(operacion.entidad())
                .idEntidad(operacion.idEntidad())
                .valorAnterior(json(operacion.valorAnterior()))
                .valorNuevo(json(operacion.valorNuevo()))
                .motivo(recortar(operacion.motivo()))
                .fechaHora(Tiempo.ahora(reloj))
                .build());
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoria> consultarSobreServidor(Integer idServidor, String operacion) {
        return repositorio.findByIdServidorAndOperacionOrderByFechaHoraDescIdDesc(idServidor, operacion);
    }

    private String json(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return mapeadorJson.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            // Un valor que no se puede serializar no impide la operación: se registra su tipo
            return "{\"noSerializable\":\"" + valor.getClass().getSimpleName() + "\"}";
        }
    }

    private static String recortar(String motivo) {
        return motivo == null || motivo.length() <= 1000 ? motivo : motivo.substring(0, 1000);
    }

    public record Operacion(String nombre, String entidad, Integer idEntidad,
                            Integer idServidor, Integer idGrupo, Integer idOrden,
                            Object valorAnterior, Object valorNuevo, String motivo) {

        public static Operacion de(String nombre, String entidad, Integer idEntidad) {
            return new Operacion(nombre, entidad, idEntidad, null, null, null, null, null, null);
        }

        public Operacion sobreServidor(Integer id) {
            return new Operacion(nombre, entidad, idEntidad, id, idGrupo, idOrden, valorAnterior, valorNuevo, motivo);
        }

        public Operacion sobreGrupo(Integer id) {
            return new Operacion(nombre, entidad, idEntidad, idServidor, id, idOrden, valorAnterior, valorNuevo, motivo);
        }

        public Operacion sobreOrden(Integer id) {
            return new Operacion(nombre, entidad, idEntidad, idServidor, idGrupo, id, valorAnterior, valorNuevo, motivo);
        }

        public Operacion valores(Object anterior, Object nuevo) {
            return new Operacion(nombre, entidad, idEntidad, idServidor, idGrupo, idOrden, anterior, nuevo, motivo);
        }

        public Operacion motivo(String texto) {
            return new Operacion(nombre, entidad, idEntidad, idServidor, idGrupo, idOrden, valorAnterior, valorNuevo, texto);
        }
    }
}
