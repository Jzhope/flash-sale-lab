package dev.jzhope.flashsale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "dev.jzhope.flashsale",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
  // 分层约定要由测试保护，避免项目变大后 Controller 又直接访问数据库。
  @ArchTest
  static final ArchRule controllersDoNotAccessRepositories =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..");

  @ArchTest
  static final ArchRule onlyRepositoriesUseJdbc =
      noClasses()
          .that()
          .resideOutsideOfPackage("..repository..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework.jdbc..");

  @ArchTest
  static final ArchRule businessAndPersistenceDoNotDependOnControllers =
      noClasses()
          .that()
          .resideInAnyPackage("..service..", "..repository..", "..model..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..controller..");

  @ArchTest
  static final ArchRule modelsDoNotDependOnWebOrPersistence =
      noClasses()
          .that()
          .resideInAPackage("..model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..controller..", "..service..", "..repository..", "org.springframework..");
}
