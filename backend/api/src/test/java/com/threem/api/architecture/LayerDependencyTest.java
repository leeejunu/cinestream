package com.threem.api.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * docs/architecture.md의 의존성 규칙.
 */
@AnalyzeClasses(packages = "com.threem.api", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

    @ArchTest
    static final ArchRule domain은_바깥_계층과_Spring을_모른다 = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..", "..presentation..",
                    "..global.security..", "..global.config..", "org.springframework..");

    @ArchTest
    static final ArchRule application은_infrastructure와_presentation을_모른다 = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..presentation..");

    @ArchTest
    static final ArchRule infrastructure는_presentation을_모른다 = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..");

    @ArchTest
    static final ArchRule presentation은_리포지토리와_infrastructure를_직접_쓰지_않는다 = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..domain.repository..");

    @ArchTest
    static final ArchRule global_error는_순수_자바다 = noClasses()
            .that().resideInAPackage("..global.error..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");
}
