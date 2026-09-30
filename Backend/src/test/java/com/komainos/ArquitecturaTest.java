package com.komainos;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Estructura del backend (R2.2, DEC-27, DEC-33). Cada componente de la vista de
 * componentes es un paquete, y dentro de él las clases se agrupan por su rol:
 * controller, dto, mapper, service, model, event, repository y config.
 *
 * <p>Estas reglas corren con {@code mvn test}: una dependencia que rompa la
 * arquitectura hace fallar la compilación de la iteración, en lugar de
 * descubrirse al leer el código.
 */
@AnalyzeClasses(packages = "com.komainos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    // ------------------------------------------------------------ componentes

    /** Los componentes de R2.2 dependen unos de otros en una sola dirección. */
    @ArchTest
    static final ArchRule componentesSinCiclos = slices().matching("com.komainos.(*)..")
            .should().beFreeOfCycles();

    /** DEC-27: el inventario no conoce la planificación ni el ciclo de mantenimiento. */
    @ArchTest
    static final ArchRule inventarioIndependiente = noClasses().that().resideInAPackage("com.komainos.inventario..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.komainos.planificacion..", "com.komainos.mantenimiento..");

    /** DEC-33: seguridad obtiene del inventario lo que necesita a través de puertos. */
    @ArchTest
    static final ArchRule seguridadIndependiente = noClasses().that().resideInAPackage("com.komainos.seguridad..")
            .should().dependOnClassesThat().resideInAnyPackage("com.komainos.inventario..",
                    "com.komainos.planificacion..", "com.komainos.mantenimiento..");

    /** {@code shared} es transversal: lo usan todos y no depende de ningún componente. */
    @ArchTest
    static final ArchRule sharedTransversal = noClasses().that().resideInAPackage("com.komainos.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("com.komainos.auditoria..",
                    "com.komainos.inventario..", "com.komainos.mantenimiento..", "com.komainos.planificacion..",
                    "com.komainos.seguridad..");

    // ------------------------------------------------------------------ capas

    /** El controlador solo traduce: llega a los datos a través de los servicios. */
    @ArchTest
    static final ArchRule controladoresSinRepositorios = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    /**
     * Los servicios devuelven entidades y no conocen el contrato HTTP: así el
     * mismo caso de uso sirve a la API, a los procesos programados y a la
     * importación masiva.
     */
    @ArchTest
    static final ArchRule nucleoSinHttp = noClasses()
            .that().resideInAnyPackage("..service..", "..model..", "..event..", "..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..dto..", "..mapper..");

    /** Entidades y eventos no dependen de servicios ni de repositorios. */
    @ArchTest
    static final ArchRule modeloSinDependencias = noClasses().that().resideInAnyPackage("..model..", "..event..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..");

    /** Los repositorios se usan desde los servicios (y desde la configuración de arranque). */
    @ArchTest
    static final ArchRule repositoriosDesdeServicios = classes().that().resideInAPackage("..repository..")
            .should().onlyBeAccessed().byAnyPackage("..repository..", "..service..", "..config..");

    // ------------------------------------------------------- ubicación por rol

    @ArchTest
    static final ArchRule controladoresEnController = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule serviciosEnService = classes().that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule entidadesEnModel = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..model..");

    @ArchTest
    static final ArchRule repositoriosEnRepository = classes().that().areInterfaces().and().areAssignableTo(Repository.class)
            .should().resideInAPackage("..repository..");
}
