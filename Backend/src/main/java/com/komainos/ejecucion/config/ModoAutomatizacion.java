package com.komainos.ejecucion.config;

/** SIMULADO responde sin conectarse a los servidores; REAL invoca ansible-playbook desde un controlador Linux */
public enum ModoAutomatizacion {
    SIMULADO,
    REAL
}
