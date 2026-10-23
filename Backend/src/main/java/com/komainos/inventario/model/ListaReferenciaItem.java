package com.komainos.inventario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Elemento de una lista de referencia con su vigencia (R2.4): no se elimina, al retirarlo se registra su fecha de baja,
 * y la fecha de alta forma parte de la clave para que un valor retirado pueda volver a agregarse.
 */
@Entity
@Table(name = "lista_referencia_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListaReferenciaItem {

    @EmbeddedId
    private Clave clave;

    @MapsId("idListaReferencia")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lista_referencia")
    private ListaReferencia lista;

    /** Nula mientras está vigente (ck_lista_referencia_item_vigencia: posterior al alta) */
    @Column(name = "fecha_baja")
    private Instant fechaBaja;

    static ListaReferenciaItem nuevo(ListaReferencia lista, String valor, Instant alta) {
        ListaReferenciaItem item = new ListaReferenciaItem();
        item.lista = lista;
        item.clave = new Clave(lista.getId(), valor, alta);
        return item;
    }

    public String getValor() {
        return clave.getValor();
    }

    public Instant getFechaAlta() {
        return clave.getFechaAlta();
    }

    public boolean estaVigente() {
        return fechaBaja == null;
    }

    /** Vigente en ese instante: el alta se incluye y la baja no */
    public boolean vigenteEn(Instant momento) {
        return !getFechaAlta().isAfter(momento) && (fechaBaja == null || fechaBaja.isAfter(momento));
    }

    void retirar(Instant baja) {
        fechaBaja = baja;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Clave implements Serializable {

        @Column(name = "id_lista_referencia")
        private Integer idListaReferencia;

        @Column(name = "valor", nullable = false, length = 255)
        private String valor;

        @Column(name = "fecha_alta", nullable = false)
        private Instant fechaAlta;

        Clave(Integer idListaReferencia, String valor, Instant fechaAlta) {
            this.idListaReferencia = idListaReferencia;
            this.valor = valor;
            this.fechaAlta = fechaAlta;
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Clave c && Objects.equals(idListaReferencia, c.idListaReferencia)
                    && Objects.equals(valor, c.valor) && Objects.equals(fechaAlta, c.fechaAlta);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idListaReferencia, valor, fechaAlta);
        }
    }
}
