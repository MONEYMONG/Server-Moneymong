package com.moneymong.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 패키지 의존 방향 규칙.
 *
 * <p>기존 위반은 {@code src/test/resources/archunit_store}에 기록돼 있고, 새로 생기는 위반만 실패한다.
 * 위반을 고치면 store에서 해당 항목이 자동으로 지워지므로, 바뀐 store 파일도 함께 commit한다.
 */
@AnalyzeClasses(packages = "com.moneymong", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // domain.agency, domain.ledger, global.security 같은 2단계 패키지 단위
    @ArchTest
    static final ArchRule 도메인_간_순환_의존이_없다 = freeze(
            slices().matching("com.moneymong.(*).(*)..")
                    .should().beFreeOfCycles()
    );

    // 멀티모듈 분리 시 common module로 갈 패키지. domain이나 web 계층(config, security filter 등)을 알면 안 된다.
    // global의 나머지 패키지(config, security filter, image, version, applink)는 api module로 가므로 domain을 참조해도 된다.
    @ArchTest
    static final ArchRule common_패키지는_domain과_web_계층에_의존하지_않는다 =
            noClasses().that().resideInAnyPackage(
                            "com.moneymong.global.exception..",
                            "com.moneymong.global.constant..",
                            "com.moneymong.global.domain..",
                            "com.moneymong.utils..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.moneymong.domain..",
                            "com.moneymong.global.config..",
                            "com.moneymong.global.security..",
                            "com.moneymong.global.image..",
                            "com.moneymong.global.version..",
                            "com.moneymong.global.applink..");

    @ArchTest
    static final ArchRule entity는_service와_api에_의존하지_않는다 = freeze(
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..api..")
    );

    @ArchTest
    static final ArchRule repository는_service와_api에_의존하지_않는다 = freeze(
            noClasses().that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..api..")
    );

    @ArchTest
    static final ArchRule service는_controller에_의존하지_않는다 = freeze(
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
    );

    @ArchTest
    static final ArchRule service는_api_DTO에_의존하지_않는다 = freeze(
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..api..")
    );
}
