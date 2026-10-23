package com.komainos.inventario.model;

/** A quién reemplaza los valores una excepción: exactamente uno de los cuatro (ck_umbral_excepcion_alcance) */
public record ObjetivoExcepcion(AlcanceExcepcion alcance, Servidor servidor, GrupoMantenimiento grupo, Entorno entorno,
                                NivelCriticidad criticidad) {

    public static ObjetivoExcepcion de(Servidor servidor) {
        return new ObjetivoExcepcion(AlcanceExcepcion.SERVIDOR, servidor, null, null, null);
    }

    public static ObjetivoExcepcion de(GrupoMantenimiento grupo) {
        return new ObjetivoExcepcion(AlcanceExcepcion.GRUPO, null, grupo, null, null);
    }

    public static ObjetivoExcepcion de(Entorno entorno) {
        return new ObjetivoExcepcion(AlcanceExcepcion.ENTORNO, null, null, entorno, null);
    }

    public static ObjetivoExcepcion de(NivelCriticidad criticidad) {
        return new ObjetivoExcepcion(AlcanceExcepcion.CRITICIDAD, null, null, null, criticidad);
    }

    public Integer idObjetivo() {
        return switch (alcance) {
            case SERVIDOR -> servidor.getId();
            case GRUPO -> grupo.getId();
            case ENTORNO -> entorno.getId();
            case CRITICIDAD -> criticidad.getId();
        };
    }

    public String nombre() {
        return switch (alcance) {
            case SERVIDOR -> servidor.getHostname();
            case GRUPO -> grupo.getNombre();
            case ENTORNO -> entorno.getNombre();
            case CRITICIDAD -> criticidad.getNombre();
        };
    }
}
