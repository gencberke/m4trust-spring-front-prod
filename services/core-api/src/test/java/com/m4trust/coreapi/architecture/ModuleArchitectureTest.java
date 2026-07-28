package com.m4trust.coreapi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.bind.annotation.RequestBody;

class ModuleArchitectureTest {

  // sharedkernel is an empty scaffold (ADR-004); deployment is a single-class Boot entry
  // point with no api/domain/infra split — neither belongs in layered-module rules.
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

  private static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FIRST_PARTY_API_OR_INFRA =
      noClasses()
          .that()
          .resideInAPackage("com.m4trust.coreapi..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.m4trust.coreapi..api..", "com.m4trust.coreapi..infra..")
          .because(
              "domain packages must not depend on any first-party api or infra package,"
                  + " whether same-module or foreign");

  private static final ArchRule CUSTOM_REQUEST_BODIES_LIVE_IN_API_PACKAGES =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("com.m4trust.coreapi..api..")
          .and()
          .areMetaAnnotatedWith(org.springframework.web.bind.annotation.RequestMapping.class)
          .should(requestBodyParametersResideInApiPackages())
          .allowEmptyShould(true);

  @Test
  void topLevelModulesAreFreeOfCycles() {
    JavaClasses productionClasses = productionClasses();

    slices()
        .matching("com.m4trust.coreapi.(*)..")
        .should()
        .beFreeOfCycles()
        .because("module collaboration must not create cyclic ownership")
        .check(productionClasses);
  }

  @Test
  void repositoriesAreOnlyAccessedFromOwningModule() {
    JavaClasses productionClasses = productionClasses();

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
          .because(
              "ADR-003 §23 restricts repository access to the owning module's own package tree")
          .allowEmptyShould(true)
          .check(productionClasses);
    }
  }

  @Test
  void domainLayersDoNotDependOnAnyFirstPartyApiOrInfraLayers() {
    DOMAIN_MUST_NOT_DEPEND_ON_FIRST_PARTY_API_OR_INFRA.check(productionClasses());
  }

  @Test
  void customControllerRequestBodiesResideInApiPackages() {
    CUSTOM_REQUEST_BODIES_LIVE_IN_API_PACKAGES.check(productionClasses());
  }

  @Test
  void fulfillmentDoesNotDependOnContractIntelligenceOrDocumentModules() {
    JavaClasses productionClasses = productionClasses();

    noClasses()
        .that()
        .resideInAPackage("com.m4trust.coreapi.fulfillment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.m4trust.coreapi.contractintelligence..", "com.m4trust.coreapi.document..")
        .because("fulfillment owns video analysis without reusing document-analysis ownership")
        .check(productionClasses);
  }

  @Test
  void fulfillmentDoesNotDependOnIntegrationModule() {
    JavaClasses productionClasses = productionClasses();

    noClasses()
        .that()
        .resideInAPackage("com.m4trust.coreapi.fulfillment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.m4trust.coreapi.integration..")
        .because(
            "fulfillment owns video analysis commands through a fulfillment port implemented by"
                + " integration")
        .check(productionClasses);
  }

  @Test
  void paymentBusinessServicesDoNotReachProviderIntegrationCapabilities() {
    JavaClasses productionClasses = productionClasses();

    noClasses()
        .that()
        .resideInAPackage("com.m4trust.coreapi.payment..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.m4trust.coreapi.integration.infra.payment..")
        .because(
            "payment business services own only the neutral funding port; Moka pool probes stay"
                + " integration-only")
        .check(productionClasses);
  }

  @Test
  void caseworkDoesNotDependOnDealOrFulfillmentModules() {
    JavaClasses productionClasses = productionClasses();

    noClasses()
        .that()
        .resideInAPackage("com.m4trust.coreapi.casework..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.m4trust.coreapi.deal..", "com.m4trust.coreapi.fulfillment..")
        .because(
            "casework collaborates through consumer-owned ports implemented by deal and fulfillment")
        .check(productionClasses);
  }

  static Arguments[] domainBoundaryViolationCases() {
    return new Arguments[] {
      Arguments.of(
          "own-api",
          new Class<?>[] {
            com.m4trust.coreapi.fixtures.sample.domain.DomainDependingOnOwnApi.class,
            com.m4trust.coreapi.fixtures.sample.api.OwnApiType.class
          }),
      Arguments.of(
          "foreign-api",
          new Class<?>[] {
            com.m4trust.coreapi.fixtures.sample.domain.DomainDependingOnForeignApi.class,
            com.m4trust.coreapi.fixtures.foreign.api.ForeignApiType.class
          }),
      Arguments.of(
          "own-infra",
          new Class<?>[] {
            com.m4trust.coreapi.fixtures.sample.domain.DomainDependingOnOwnInfra.class,
            com.m4trust.coreapi.fixtures.sample.infra.OwnInfraType.class
          }),
      Arguments.of(
          "foreign-infra",
          new Class<?>[] {
            com.m4trust.coreapi.fixtures.sample.domain.DomainDependingOnForeignInfra.class,
            com.m4trust.coreapi.fixtures.foreign.infra.ForeignInfraType.class
          })
    };
  }

  @ParameterizedTest(name = "domain boundary rejects {0}")
  @MethodSource("domainBoundaryViolationCases")
  void architectureFixturesProveEachDomainBoundaryViolationIndependently(
      String category, Class<?>[] classes) {
    JavaClasses violating = new ClassFileImporter().importClasses(classes);
    assertThrows(
        AssertionError.class,
        () -> DOMAIN_MUST_NOT_DEPEND_ON_FIRST_PARTY_API_OR_INFRA.check(violating),
        () -> "expected independent failure for " + category);
  }

  @Test
  void architectureFixturesAllowDomainPortDependency() {
    JavaClasses allowed =
        new ClassFileImporter()
            .importClasses(
                com.m4trust.coreapi.fixtures.sample.domain.DomainDependingOnPort.class,
                com.m4trust.coreapi.fixtures.sample.domain.port.AllowedDomainPort.class);
    assertDoesNotThrow(() -> DOMAIN_MUST_NOT_DEPEND_ON_FIRST_PARTY_API_OR_INFRA.check(allowed));
  }

  @Test
  void architectureFixturesProveRequestBodyOutsideApiFails() {
    JavaClasses violating =
        new ClassFileImporter()
            .importClasses(
                com.m4trust.coreapi.fixtures.sample.api.ControllerWithDomainRequestBody.class,
                com.m4trust.coreapi.fixtures.sample.domain.DomainRequestBody.class);

    assertThrows(
        AssertionError.class, () -> CUSTOM_REQUEST_BODIES_LIVE_IN_API_PACKAGES.check(violating));

    JavaClasses allowed =
        new ClassFileImporter()
            .importClasses(
                com.m4trust.coreapi.fixtures.sample.api.ControllerWithApiRequestBody.class,
                com.m4trust.coreapi.fixtures.sample.api.ApiRequestBody.class);
    assertDoesNotThrow(() -> CUSTOM_REQUEST_BODIES_LIVE_IN_API_PACKAGES.check(allowed));
  }

  @Test
  void architectureFixturesProveNearMissApiSubstringPackageFails() {
    JavaClasses nearMiss =
        new ClassFileImporter()
            .importClasses(
                com.m4trust.coreapi.fixtures.sample.api.ControllerWithNearMissRequestBody.class,
                com.m4trust.coreapi.fixtures.sample.apimodel.NearMissRequestBody.class);

    assertThrows(
        AssertionError.class,
        () -> CUSTOM_REQUEST_BODIES_LIVE_IN_API_PACKAGES.check(nearMiss),
        "apimodel must not count as an api package segment");
  }

  private static JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.m4trust.coreapi");
  }

  private static ArchCondition<JavaMethod> requestBodyParametersResideInApiPackages() {
    return new ArchCondition<>("have @RequestBody first-party types in an api package") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (JavaParameter parameter : method.getParameters()) {
          if (!parameter.isAnnotatedWith(RequestBody.class)) {
            continue;
          }
          JavaClass type = parameter.getRawType();
          if (!type.getPackageName().startsWith("com.m4trust.coreapi.")) {
            continue;
          }
          if (!hasExactPackageSegment(type.getPackageName(), "api")) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName()
                        + " binds @RequestBody type "
                        + type.getName()
                        + " outside an api package segment"));
          }
        }
      }
    };
  }

  static boolean hasExactPackageSegment(String packageName, String segment) {
    for (String part : packageName.split("\\.")) {
      if (part.equals(segment)) {
        return true;
      }
    }
    return false;
  }
}
