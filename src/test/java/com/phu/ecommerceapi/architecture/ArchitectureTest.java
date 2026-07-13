package com.phu.ecommerceapi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

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
    void activeControllersMustLiveInModularApiPackages() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .or()
                .areAnnotatedWith(Controller.class)
                .should()
                .resideInAnyPackage("..api..")
                .check(CLASSES);
    }

    @Test
    void legacyPrototypePackagesMustNotExposeSpringPublicBoundaries() {
        noClasses()
                .that()
                .resideInAnyPackage("..User..", "..Product..", "..Security..", "..Stripe..", "..CartItem..")
                .should()
                .beAnnotatedWith(RestController.class)
                .orShould()
                .beAnnotatedWith(Controller.class)
                .orShould()
                .beAnnotatedWith(Configuration.class)
                .check(CLASSES);
    }

    @Test
    void modularControllersMustNotReturnPersistenceTypesDirectly() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAnyPackage("..api..")
                .and()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .should(notReturnPersistenceType())
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void applicationPackagesMustNotDependOnModuleApiDtos() {
        noClasses()
                .that()
                .resideInAnyPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..audit.api..",
                        "..cart.api..",
                        "..catalog.api..",
                        "..checkout.api..",
                        "..customer.api..",
                        "..inventory.api..",
                        "..ledger.api..",
                        "..order.api..",
                        "..payment.api..",
                        "..reconciliation.api.."
                )
                .check(CLASSES);
    }

    @Test
    void applicationPackagesMustNotDependOnStripeSdk() {
        noClasses()
                .that()
                .resideInAnyPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.stripe..")
                .check(CLASSES);
    }

    @Test
    void applicationPackagesMustNotDependOnLegacyJpaPackages() {
        noClasses()
                .that()
                .resideInAnyPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..User..", "..Product..", "..CartItem..")
                .check(CLASSES);

        noClasses()
                .that()
                .resideInAnyPackage("..application..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.phu.ecommerceapi.cart.infrastructure.CartItemModel")
                .check(CLASSES);
    }

    @Test
    void jpaEntitySourcesMustNotUseLombokData() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ArchitectureTest::isJpaEntitySourceWithLombokData)
                    .map(sourceRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertThat(violations)
                .as("JPA entity source files using Lombok @Data")
                .isEmpty();
    }

    @Test
    void modularApplicationPackagesMustUsePortsInsteadOfInfrastructure() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "..audit.application..",
                        "..cart.application..",
                        "..catalog.application..",
                        "..checkout.application..",
                        "..customer.application..",
                        "..inventory.application..",
                        "..ledger.application..",
                        "..order.application..",
                        "..outbox.application..",
                        "..payment.application..",
                        "..reconciliation.application.."
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void moneySensitivePackagesMustNotExposePrimitiveFloatingPoint() {
        classes()
                .that()
                .resideInAnyPackage(
                        "..Product..",
                        "..catalog..",
                        "..cart..",
                        "..checkout..",
                        "..order..",
                        "..payment..",
                        "..ledger..",
                        "..reconciliation.."
                )
                .should(notUsePrimitiveFloatingPointInFieldsOrSignatures())
                .check(CLASSES);
    }

    @Test
    void ownershipSensitiveApplicationCodeMustNotUseMutableIdentityClaims() {
        classes()
                .that()
                .resideInAnyPackage(
                        "..cart.application..",
                        "..checkout.application..",
                        "..customer.application..",
                        "..payment.application.."
                )
                .should(notCallCurrentUserUsernameOrEmail())
                .check(CLASSES);
    }

    private ArchCondition<JavaMethod> notReturnPersistenceType() {
        return new ArchCondition<>("not return JPA entities or infrastructure records directly") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Set<JavaClass> returnedTypes = method.getReturnType().getAllInvolvedRawTypes();
                List<String> persistenceTypes = returnedTypes.stream()
                        .filter(ArchitectureTest::isPersistenceType)
                        .map(JavaClass::getName)
                        .sorted()
                        .toList();
                String message = method.getFullName() + " returns persistence types " + persistenceTypes;
                events.add(new SimpleConditionEvent(method, persistenceTypes.isEmpty(), message));
            }
        };
    }

    private ArchCondition<JavaClass> notUsePrimitiveFloatingPointInFieldsOrSignatures() {
        return new ArchCondition<>("not expose primitive double/float in fields or method signatures") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                List<String> violations = new ArrayList<>();
                for (JavaField field : javaClass.getFields()) {
                    if (isPrimitiveFloatingPoint(field.getRawType())) {
                        violations.add(field.getFullName());
                    }
                }
                for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
                    if (isPrimitiveFloatingPoint(codeUnit.getRawReturnType())) {
                        violations.add(codeUnit.getFullName() + " return type");
                    }
                    for (JavaClass parameterType : codeUnit.getRawParameterTypes()) {
                        if (isPrimitiveFloatingPoint(parameterType)) {
                            violations.add(codeUnit.getFullName() + " parameter type");
                        }
                    }
                }
                String message = javaClass.getName() + " exposes primitive floating point types " + violations;
                events.add(new SimpleConditionEvent(javaClass, violations.isEmpty(), message));
            }
        };
    }

    private ArchCondition<JavaClass> notCallCurrentUserUsernameOrEmail() {
        return new ArchCondition<>("use durable subject identity instead of username/email ownership claims") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                List<String> violations = javaClass.getMethodCallsFromSelf().stream()
                        .filter(ArchitectureTest::callsMutableCurrentUserClaim)
                        .map(call -> call.getSourceCodeLocation().toString())
                        .sorted()
                        .toList();
                String message = javaClass.getName()
                        + " calls CurrentUser username/email accessors " + violations;
                events.add(new SimpleConditionEvent(javaClass, violations.isEmpty(), message));
            }
        };
    }

    private static boolean isPersistenceType(JavaClass javaClass) {
        return javaClass.isAnnotatedWith(Entity.class)
                || javaClass.getPackageName().contains(".infrastructure");
    }

    private static boolean isPrimitiveFloatingPoint(JavaClass javaClass) {
        return javaClass.isEquivalentTo(double.class) || javaClass.isEquivalentTo(float.class);
    }

    private static boolean callsMutableCurrentUserClaim(JavaMethodCall call) {
        String ownerName = call.getTargetOwner().getName();
        String targetName = call.getTarget().getName();
        return ownerName.equals("com.phu.ecommerceapi.identity.application.CurrentUser")
                && (targetName.equals("username") || targetName.equals("email"));
    }

    private static boolean isJpaEntitySourceWithLombokData(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("@Entity") && source.contains("@Data");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read source file " + path, exception);
        }
    }
}
