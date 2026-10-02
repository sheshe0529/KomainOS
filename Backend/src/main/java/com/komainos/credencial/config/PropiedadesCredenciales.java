package com.komainos.credencial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** llaveMaestra: AES-256 en base64, viene del entorno y nunca de la base ni del código (RNF02, RNF03) */
@ConfigurationProperties(prefix = "komainos.credenciales")
public record PropiedadesCredenciales(String llaveMaestra, int segundosRevelado) {
}
