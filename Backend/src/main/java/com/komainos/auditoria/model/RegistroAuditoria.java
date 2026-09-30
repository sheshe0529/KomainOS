package com.komainos.auditoria.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Registro de la bitacora de operaciones (RNF06), tabla {@code auditoria}.
 *
 * <p>Solo se inserta: R2.4 establece que no se modifican ni eliminan registros,
 * por eso la entidad es inmutable y no expone setters. Las referencias a
 * servidor, grupo y orden se guardan como identificadores simples para que un
 * registro de auditoria nunca cargue ni bloquee la entidad afectada.
 */
@Entity
@Table(name = "auditoria")
@Immutable
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Integer id;

    /** Usuario que ejecuto la operacion; nulo si fue un proceso del Sistema. */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "id_servidor")
    private Integer idServidor;

    @Column(name = "id_grupo_mantenimiento")
    private Integer idGrupoMantenimiento;

    @Column(name = "id_orden")
    private Integer idOrden;

    @Column(name = "operacion", nullable = false, length = 255)
    private String operacion;

    @Column(name = "entidad", nullable = false, length = 255)
    private String entidad;

    @Column(name = "id_entidad")
    private Integer idEntidad;

    /** Valores previos, serializados en JSON y sin secretos (RNF11). */
    @Column(name = "valor_anterior", columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "text")
    private String valorNuevo;

    @Column(name = "motivo", length = 1000)
    private String motivo;

    /** Proceso automatico que ejecuto la operacion; nulo si fue un usuario. */
    @Column(name = "proceso", length = 255)
    private String proceso;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
}
