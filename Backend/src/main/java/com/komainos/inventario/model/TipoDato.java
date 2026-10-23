package com.komainos.inventario.model;

import com.komainos.shared.exception.ReglaNegocioException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Locale;

/**
 * Tipo literal de una métrica o de los elementos de una lista de referencia (enum_tipo_dato_parametro, R2.4). Una métrica
 * escalar guarda su medición en medicion_metrica.valor, que es numérico; una de lista guarda cada elemento como texto.
 */
public enum TipoDato {

    STRING("Texto"),
    INTEGER("Entero"),
    DECIMAL("Decimal"),
    BOOLEAN("Booleano");

    private static final int LARGO_MAXIMO = 255;
    private static final BigDecimal LIMITE_NUMERICO = new BigDecimal("1E14");

    private final String etiqueta;

    TipoDato(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** La medición escalar es numérica: un texto suelto no tiene dónde guardarse */
    public boolean admiteMetricaEscalar() {
        return this != STRING;
    }

    /** Un conjunto de verdadero y falso no aporta nada como lista de referencia */
    public boolean admiteLista() {
        return this != BOOLEAN;
    }

    public boolean esNumerico() {
        return this == INTEGER || this == DECIMAL;
    }

    /**
     * Forma canónica de un elemento de lista (lista_referencia_item.valor): un entero sin ceros a la izquierda, un
     * decimal sin ceros finales y un texto recortado, en minúsculas si la lista no distingue mayúsculas.
     */
    public String canonico(String crudo, boolean ignoraMayusculas) {
        String valor = crudo == null ? "" : crudo.trim();
        if (valor.isEmpty()) {
            throw new ReglaNegocioException("Los elementos de la lista no pueden estar vacíos");
        }
        String canonico = switch (this) {
            case INTEGER -> entero(valor).toString();
            case DECIMAL -> decimal(valor).stripTrailingZeros().toPlainString();
            case STRING -> {
                if (valor.matches(".*[,;\\r\\n].*")) {
                    throw new ReglaNegocioException("El elemento «%s» no puede contener comas, punto y coma ni saltos de línea"
                            .formatted(valor));
                }
                yield ignoraMayusculas ? valor.toLowerCase(Locale.ROOT) : valor;
            }
            case BOOLEAN -> throw new ReglaNegocioException("Las listas de referencia no admiten elementos booleanos");
        };
        if (canonico.length() > LARGO_MAXIMO) {
            throw new ReglaNegocioException("El elemento «%s…» supera los %d caracteres"
                    .formatted(canonico.substring(0, 20), LARGO_MAXIMO));
        }
        return canonico;
    }

    /** Orden de presentación: numérico para números y alfabético para texto */
    public Comparator<String> orden() {
        return esNumerico() ? Comparator.comparing(BigDecimal::new) : Comparator.naturalOrder();
    }

    /** Un límite de umbral debe poder compararse con la medición: numeric(20,6) y del tipo de la métrica */
    public void validarLimite(BigDecimal limite, String campo) {
        if (limite == null) {
            return;
        }
        if (limite.abs().compareTo(LIMITE_NUMERICO) >= 0) {
            throw new ReglaNegocioException("El valor %s es demasiado grande".formatted(campo));
        }
        if (limite.stripTrailingZeros().scale() > 6) {
            throw new ReglaNegocioException("El valor %s admite hasta 6 decimales".formatted(campo));
        }
        if (this == INTEGER && limite.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException("La métrica es entera: el valor %s no puede tener decimales".formatted(campo));
        }
        if (this == BOOLEAN && limite.compareTo(BigDecimal.ZERO) != 0 && limite.compareTo(BigDecimal.ONE) != 0) {
            throw new ReglaNegocioException("La métrica es booleana: el valor %s debe ser 0 o 1".formatted(campo));
        }
    }

    private static BigInteger entero(String valor) {
        try {
            return new BigInteger(valor);
        } catch (NumberFormatException ex) {
            throw new ReglaNegocioException("El elemento «%s» no es un número entero".formatted(valor));
        }
    }

    private static BigDecimal decimal(String valor) {
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException ex) {
            throw new ReglaNegocioException("El elemento «%s» no es un número".formatted(valor));
        }
    }
}
