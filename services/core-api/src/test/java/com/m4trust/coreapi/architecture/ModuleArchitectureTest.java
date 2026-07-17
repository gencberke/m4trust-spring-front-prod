package com.m4trust.coreapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

    private static final String[] MODULES = {
        "api", "audit", "deal", "identity", "integration", "organization", "sharedkernel"
    };

    @Test
    void topLevelModulesAreFreeOfCycles() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.m4trust.coreapi");

        slices()
                .matching("com.m4trust.coreapi.(*)..")
                .should().beFreeOfCycles()
                .because("module collaboration must not create cyclic ownership")
                .check(productionClasses);
    }

    @Test
    void repositoriesAreOnlyAccessedFromOwningModule() {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.m4trust.coreapi");

        for (String module : MODULES) {
            classes()
                    .that().resideInAPackage("com.m4trust.coreapi." + module + "..")
                    .and().haveSimpleNameEndingWith("Repository")
                    .should().onlyBeAccessed().byClassesThat()
                    .resideInAnyPackage("com.m4trust.coreapi." + module + "..")
                    .because(
                            "ADR-003 §23 restricts repository access to the owning module's own package tree")
                    .allowEmptyShould(true)
                    .check(productionClasses);
        }
    }
}
