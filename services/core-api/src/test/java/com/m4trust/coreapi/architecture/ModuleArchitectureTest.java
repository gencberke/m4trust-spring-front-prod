package com.m4trust.coreapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Small, generic ownership proof. Feature-specific dependency direction belongs to module code
 * review, not a growing architectural test matrix.
 */
class ModuleArchitectureTest {

  private static final String[] MODULES = {
    "api",
    "audit",
    "casework",
    "contractintelligence",
    "contracts",
    "deal",
    "document",
    "fulfillment",
    "idempotency",
    "identity",
    "integration",
    "organization",
    "payment",
    "ratification",
    "sharedkernel"
  };

  private static JavaClasses productionClasses;

  private static final ArchRule DOMAIN_HAS_NO_FIRST_PARTY_API_OR_INFRA =
      noClasses()
          .that()
          .resideInAPackage("com.m4trust.coreapi..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.m4trust.coreapi..api..", "com.m4trust.coreapi..infra..")
          .because("domain code must not depend on first-party transport or infrastructure");

  @BeforeAll
  static void importProductionClassesOnce() {
    productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.m4trust.coreapi");
  }

  @Test
  void topLevelModulesAreFreeOfCycles() {
    slices()
        .matching("com.m4trust.coreapi.(*)..")
        .should()
        .beFreeOfCycles()
        .because("module collaboration must not create cyclic ownership")
        .check(productionClasses);
  }

  @Test
  void domainLayersDoNotDependOnFirstPartyApiOrInfra() {
    DOMAIN_HAS_NO_FIRST_PARTY_API_OR_INFRA.check(productionClasses);
  }

  @Test
  void repositoriesAreOnlyAccessedFromTheirOwningModule() {
    for (String module : MODULES) {
      classes()
          .that()
          .resideInAPackage("com.m4trust.coreapi." + module + "..")
          .and()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .onlyBeAccessed()
          .byClassesThat()
          .resideInAnyPackage("com.m4trust.coreapi." + module + "..")
          .allowEmptyShould(true)
          .check(productionClasses);
    }
  }

  @Test
  void domainToApiFixtureProvesTheBoundaryCanFail() {
    JavaClasses violating =
        new ClassFileImporter()
            .importClasses(
                com.m4trust.coreapi.fixtures.sample.domain.DomainDependingOnOwnApi.class,
                com.m4trust.coreapi.fixtures.sample.api.OwnApiType.class);

    assertThrows(
        AssertionError.class, () -> DOMAIN_HAS_NO_FIRST_PARTY_API_OR_INFRA.check(violating));
  }
}
