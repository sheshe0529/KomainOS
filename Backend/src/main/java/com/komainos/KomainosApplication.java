package com.komainos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class KomainosApplication {

    static {
        // Con la JVM en UTC, Hibernate no desplaza las horas TIME al guardarlas (DEC-28)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(KomainosApplication.class, args);
    }
}
