package com.phu.ecommerceapi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.phu.ecommerceapi");

    @Test
    void domainPackagesMustNotDependOnFrameworksOrExternalAdapters() {
        noClasses()
                .that()
                .resideInAnyPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "com.fasterxml.jackson..",
                        "com.stripe..",
                        "java.sql..",
                        "javax.sql.."
                )
                .check(CLASSES);
    }

    @Test
    void domainPackagesMustNotDependOnOuterLayers() {
        noClasses()
                .that()
                .resideInAnyPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..api..", "..application..", "..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void apiPackagesMustNotDependOnInfrastructurePackages() {
        noClasses()
                .that()
                .resideInAnyPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void modularControllersMustNotReturnJpaEntitiesDirectly() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAnyPackage("..api..")
                .and()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .should(notReturnJpaEntity())
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    private ArchCondition<JavaMethod> notReturnJpaEntity() {
        return new ArchCondition<>("not return JPA entities directly") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean returnsJpaEntity = method.getRawReturnType().isAnnotatedWith(Entity.class);
                String message = method.getFullName() + " returns " + method.getRawReturnType().getName();
                events.add(new SimpleConditionEvent(method, !returnsJpaEntity, message));
            }
        };
    }
}
