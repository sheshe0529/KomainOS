package com.komainos.ejecucion.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.Map;
@ConfigurationProperties("komainos.automatizacion")
public record PropiedadesAutomatizacion(@DefaultValue("SIMULADO") String modo,
 @DefaultValue("false") boolean procesoHabilitado, @DefaultValue("ansible-playbook") String ejecutable,
 Map<String,String> escenarios) {
 public boolean simulada(){return "SIMULADO".equalsIgnoreCase(modo);}
 public String escenario(String hostname){return escenarios==null?"NORMAL":escenarios.getOrDefault(hostname,"NORMAL");}
}
