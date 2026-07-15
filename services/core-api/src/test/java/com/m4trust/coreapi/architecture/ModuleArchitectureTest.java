package com.m4trust.coreapi.architecture;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

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
}
