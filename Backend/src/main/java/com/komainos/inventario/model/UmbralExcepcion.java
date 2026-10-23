package com.komainos.inventario.model;

import com.komainos.inventario.model.Umbral.ValoresExcepcion;
import com.komainos.shared.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Valores particulares de un umbral para un servidor, un grupo de mantenimiento, un entorno o un nivel de criticidad
 * (R2.4). El alcance no cambia: para otro alcance se registra otra excepción. Las validaciones de valores viven en el
 * umbral, que conoce su modalidad y su operador.
 */
@Entity
@Table(name = "umbral_excepcion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UmbralExcepcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_umbral_excepcion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_umbral", nullable = false, updatable = false)
    private Umbral umbral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servidor", updatable = false)
    private Servidor servidor;

    /** Solo aplica en las órdenes grupales de ese grupo (R2.4) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo_mantenimiento", updatable = false)
    private GrupoMantenimiento grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_entorno", updatable = false)
    private Entorno entorno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nivel_criticidad", updatable = false)
    private NivelCriticidad nivelCriticidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lista_referencia")
    private ListaReferencia lista;

    @Column(name = "valor_advertencia", precision = 20, scale = 6)
    private BigDecimal valorAdvertencia;

    @Column(name = "valor_critico", precision = 20, scale = 6)
    private BigDecimal valorCritico;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    static UmbralExcepcion nueva(Umbral umbral, ObjetivoExcepcion objetivo, ValoresExcepcion valores, String motivo,
                                 Instant ahora) {
        UmbralExcepcion excepcion = new UmbralExcepcion();
        excepcion.umbral = umbral;
        excepcion.servidor = objetivo.servidor();
        excepcion.grupo = objetivo.grupo();
        excepcion.entorno = objetivo.entorno();
        excepcion.nivelCriticidad = objetivo.criticidad();
        excepcion.activo = true;
        excepcion.aplicar(valores, motivo, ahora);
        return excepcion;
    }

    void aplicar(ValoresExcepcion valores, String motivo, Instant ahora) {
        this.valorAdvertencia = valores.advertencia();
        this.valorCritico = valores.critico();
        this.lista = valores.lista();
        this.motivo = motivo;
        this.fechaActualizacion = ahora;
    }

    public AlcanceExcepcion alcance() {
        if (servidor != null) {
            return AlcanceExcepcion.SERVIDOR;
        }
        if (grupo != null) {
            return AlcanceExcepcion.GRUPO;
        }
        return entorno != null ? AlcanceExcepcion.ENTORNO : AlcanceExcepcion.CRITICIDAD;
    }

    public ObjetivoExcepcion objetivo() {
        return new ObjetivoExcepcion(alcance(), servidor, grupo, entorno, nivelCriticidad);
    }

    public Integer idObjetivo() {
        return objetivo().idObjetivo();
    }

    /**
     * El entorno y la criticidad se toman del servidor evaluado; el grupo, de la orden grupal en ejecución: un servidor
     * evaluado de forma individual no recibe los valores de los grupos a los que pertenece.
     */
    public boolean aplicaA(ContextoEvaluacion contexto) {
        return switch (alcance()) {
            case SERVIDOR -> Objects.equals(servidor.getId(), contexto.idServidor());
            case GRUPO -> contexto.idGrupo() != null && Objects.equals(grupo.getId(), contexto.idGrupo());
            case ENTORNO -> Objects.equals(entorno.getId(), contexto.idEntorno());
            case CRITICIDAD -> Objects.equals(nivelCriticidad.getId(), contexto.idNivelCriticidad());
        };
    }

    public void activar(Instant ahora) {
        if (activo) {
            throw new ReglaNegocioException("La excepción ya se encuentra activa");
        }
        activo = true;
        fechaActualizacion = ahora;
    }

    public void desactivar(Instant ahora) {
        if (!activo) {
            throw new ReglaNegocioException("La excepción ya se encuentra inactiva");
        }
        activo = false;
        fechaActualizacion = ahora;
    }
}
