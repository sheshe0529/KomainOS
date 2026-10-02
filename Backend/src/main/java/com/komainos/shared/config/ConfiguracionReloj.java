package com.komainos.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Inyectarlo permite probar la planificación con una hora fija */
@Configuration
public class ConfiguracionReloj {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
