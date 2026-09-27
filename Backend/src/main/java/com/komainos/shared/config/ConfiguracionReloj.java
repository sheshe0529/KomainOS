package com.komainos.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del sistema como bean.
 *
 * <p>La planificacion depende del instante actual (inicio mas temprano, fecha
 * de evaluacion). Inyectarlo permite probar esas reglas con una hora fija en
 * vez de depender de cuando corre la prueba.
 */
@Configuration
public class ConfiguracionReloj {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
