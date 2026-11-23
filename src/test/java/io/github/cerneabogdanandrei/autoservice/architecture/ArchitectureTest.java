package io.github.cerneabogdanandrei.autoservice.architecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable versions of the architecture decisions: they fail the build when the code drifts.
 */
@AnalyzeClasses(packages = "io.github.cerneabogdanandrei.autoservice", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "io.github.cerneabogdanandrei.autoservice";

    @ArchTest
    static final ArchRule constructorInjectionOnly = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule featurePackagesAreFreeOfCycles = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule commonDoesNotDependOnFeatures = noClasses()
            .that().resideInAPackage(ROOT + ".common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".auth..", ROOT + ".user..", ROOT + ".client..", ROOT + ".vehicle..",
                    ROOT + ".part..", ROOT + ".appointment..", ROOT + ".intervention..", ROOT + ".report..");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class)
            .orShould().dependOnClassesThat().areAnnotatedWith(org.springframework.stereotype.Repository.class);

    @ArchTest
    static final ArchRule controllersNeverExposeEntities = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule dtosAreRecordsWithoutEntities = classes()
            .that().resideInAPackage("..dto..")
            .should().beAssignableTo(Record.class)
            .andShould().onlyDependOnClassesThat().areNotAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule transactionBoundariesBelongToServices = noClasses()
            .that().haveSimpleNameNotEndingWith("Service")
            .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule transactionalMethodsBelongToServices = noMethods()
            .that().areDeclaredInClassesThat().haveSimpleNameNotEndingWith("Service")
            .should().beAnnotatedWith(Transactional.class);

    /**
     * Every endpoint states who may call it, so a new endpoint cannot silently fall back to
     * "any authenticated user". The login endpoint is the only public one.
     */
    @ArchTest
    static final ArchRule everyEndpointDeclaresAnAuthorizationRule = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areDeclaredInClassesThat().doNotHaveSimpleName("AuthController")
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should(declareAnAuthorizationRule());

    private static ArchCondition<JavaMethod> declareAnAuthorizationRule() {
        return new ArchCondition<>("be annotated with @PreAuthorize (or be declared in a class that is)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean secured = method.isAnnotatedWith(PreAuthorize.class)
                        || method.getOwner().isAnnotatedWith(PreAuthorize.class);
                if (!secured) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " has no @PreAuthorize rule"));
                }
            }
        };
    }
}
