package com.komainos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Punto de entrada del backend de KomainOS.
 *
 * <p>La planificacion esta habilitada desde el arranque porque buena parte del
 * sistema la conduce el actor automatizado "Sistema".
 */
@SpringBootApplication
@EnableScheduling
public class KomainosApplication {

    static {
        // La base guarda TIMESTAMP en UTC y TIME como hora de pared (DEC-06).
        // Hibernate convierte LocalTime a java.sql.Time con la zona por defecto
        // de la JVM y luego lo escribe en la zona JDBC (UTC): si ambas difieren,
        // una ventana de 01:00 se guardaria como 06:00. Con la JVM en UTC no hay
        // desplazamiento. Se fija aqui para que aplique tambien en las pruebas.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(KomainosApplication.class, args);
    }
}
