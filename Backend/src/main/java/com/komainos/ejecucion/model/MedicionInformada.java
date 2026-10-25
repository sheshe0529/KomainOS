package com.komainos.ejecucion.model;
import java.math.BigDecimal;
import java.util.List;
public record MedicionInformada(String codigo,String instancia,BigDecimal valor,List<String> elementos) {}
