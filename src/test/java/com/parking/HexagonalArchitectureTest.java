package com.parking;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the dependency rules of docs/ARCHITECTURE.md. If a change breaks
 * one of them, the build fails.
 */
@AnalyzeClasses(packages = "com.parking", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "com.parking.domain..";
    private static final String APPLICATION = "com.parking.application..";
    private static final String INFRASTRUCTURE = "com.parking.infrastructure..";

    @ArchTest
    static final ArchRule layersRespectTheDependencyDirection = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .as("infrastructure -> application -> domain");

    @ArchTest
    static final ArchRule domainDoesNotDependOnSpring = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .as("domain does not depend on Spring (JPA annotations are the only allowed framework)");

    @ArchTest
    static final ArchRule applicationDoesNotKnowWebSecurityOrJwt = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.security..",
                    "jakarta.servlet..",
                    "io.jsonwebtoken..")
            .as("application does not know HTTP, Spring Security or JWT");

    @ArchTest
    static final ArchRule inputPortsAreInterfaces = classes()
            .that().resideInAPackage("com.parking.application.port.in..")
            .and().haveSimpleNameEndingWith("UseCase")
            .should().beInterfaces()
            .as("input ports (*UseCase) are interfaces");

    @ArchTest
    static final ArchRule outputPortsAreInterfaces = classes()
            .that().resideInAPackage("com.parking.application.port.out..")
            .should().beInterfaces()
            .andShould().haveSimpleNameEndingWith("Port")
            .as("output ports are interfaces named *Port");

    @ArchTest
    static final ArchRule useCasesImplementInputPorts = classes()
            .that().resideInAPackage("com.parking.application.usecase..")
            .should().dependOnClassesThat().resideInAPackage("com.parking.application.port.in..")
            .as("use case services implement input ports");

    @ArchTest
    static final ArchRule restAdaptersOnlyUseInputPorts = noClasses()
            .that().resideInAPackage("com.parking.infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.parking.application.usecase..",
                    "com.parking.application.port.out..",
                    "com.parking.infrastructure.adapter.out.persistence..")
            .as("REST adapters talk to the application only through input ports");

    @ArchTest
    static final ArchRule outputAdaptersDoNotUseInboundAdapters = noClasses()
            .that().resideInAPackage("com.parking.infrastructure.adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAPackage("com.parking.infrastructure.adapter.in..")
            .as("persistence adapters do not depend on REST adapters");
}
