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

/**
 * Registra operaciones de negocio en la bitacora (RNF06).
 *
 * <p>Se une a la transaccion del llamador ({@code MANDATORY}): el cambio y su
 * registro se aplican juntos o no se aplica ninguno. Si se separaran, habria un
 * instante en que el dato cambio sin dejar rastro.
 *
 * <p>Quien llama es responsable de no pasar secretos en los valores (RNF11);
 * en esta iteracion ninguna entidad auditada los contiene.
 */
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

    /**
     * Operaciones de un tipo registradas sobre un servidor, la mas reciente primero.
     */
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
            // La bitacora no debe impedir la operacion por un valor que no se
            // pudo serializar; se deja constancia del tipo en su lugar.
            return "{\"noSerializable\":\"" + valor.getClass().getSimpleName() + "\"}";
        }
    }

    private static String recortar(String motivo) {
        return motivo == null || motivo.length() <= 1000 ? motivo : motivo.substring(0, 1000);
    }

    /**
     * Descripcion de una operacion auditada. Se construye con los metodos
     * {@code sobre...} para que cada llamador indique solo lo que le aplica.
     */
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
