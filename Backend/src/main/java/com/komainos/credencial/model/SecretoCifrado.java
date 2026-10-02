package com.komainos.credencial.model;

/** El tag de GCM va separado del texto cifrado porque la tabla lo guarda en su propia columna */
public record SecretoCifrado(byte[] cifrado, byte[] iv, byte[] tag, String algoritmo) {
}
