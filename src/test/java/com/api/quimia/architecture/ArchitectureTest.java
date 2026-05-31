package com.api.quimia.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Build-gate de fronteiras do template modular. Cada regra abaixo cobre
 * uma decisao arquitetural nao-negociavel; quebrar uma quebra o build.
 *
 * Documento de referencia: "Arquitetura Template Modular Java Spring.md".
 */
@AnalyzeClasses(packages = "com.api.quimia")
class ArchitectureTest {

    // ------------------------------------------------------------------
    // 1) `internal/` e privado: nenhum codigo fora de `..internal..` pode
    //    depender de classes em `..internal..` de OUTRO modulo. Importacoes
    //    dentro do mesmo modulo continuam permitidas.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule internal_is_private_across_modules =
            noClasses()
                    .should(new ArchCondition<JavaClass>(
                            "depend on `..internal..` of another domain module") {
                        @Override
                        public void check(JavaClass item, ConditionEvents events) {
                            String origin = item.getPackageName();
                            String originModule = moduleOf(origin);

                            item.getDirectDependenciesFromSelf().forEach(dep -> {
                                String target = dep.getTargetClass().getPackageName();
                                if (!target.contains(".internal")) {
                                    return;
                                }
                                String targetModule = moduleOf(target);
                                if (targetModule == null) {
                                    return;
                                }
                                if (originModule != null && originModule.equals(targetModule)) {
                                    return;
                                }
                                events.add(SimpleConditionEvent.violated(
                                        item,
                                        item.getFullName()
                                                + " (pkg " + origin + ") depends on internal of module '"
                                                + targetModule + "' at " + dep.getTargetClass().getFullName()));
                            });
                        }

                        private String moduleOf(String pkg) {
                            String marker = "com.api.quimia.domain.";
                            int idx = pkg.indexOf(marker);
                            if (idx < 0) {
                                return null;
                            }
                            String tail = pkg.substring(idx + marker.length());
                            int dot = tail.indexOf('.');
                            return dot < 0 ? tail : tail.substring(0, dot);
                        }
                    });

    // ------------------------------------------------------------------
    // 2) Gateways expostos sao interfaces. Classes terminadas em
    //    `Gateway` fora de `..internal..` representam contrato publico
    //    do modulo e PRECISAM ser interfaces.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule gateways_outside_internal_are_interfaces =
            classes()
                    .that().haveSimpleNameEndingWith("Gateway")
                    .and().resideOutsideOfPackage("..internal..")
                    .should().beInterfaces();

    // ------------------------------------------------------------------
    // 3) UseCases moram em `..internal.usecase..`. Garante que operacoes
    //    de negocio nao vazem para web/, persistence/ ou raiz do modulo.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule usecases_live_in_internal_usecase =
            classes()
                    .that().haveSimpleNameEndingWith("UseCase")
                    .should().resideInAPackage("..internal.usecase..");

    // ------------------------------------------------------------------
    // 4) Controllers moram em `..internal.web..`. Adaptador HTTP nunca
    //    vaza para outras pastas do modulo nem para a raiz publica.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule controllers_live_in_internal_web =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .or().areAnnotatedWith(Controller.class)
                    .should().resideInAPackage("..internal.web..");

    // ------------------------------------------------------------------
    // 5) Sufixo `Impl` e banido. Nomes devem descrever o que a classe
    //    faz (ex: `JpaOrderGateway`), nao o fato de ser implementacao.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule no_impl_suffix =
            noClasses().should().haveSimpleNameEndingWith("Impl");

    // ------------------------------------------------------------------
    // 6) `domain/` nao depende de `infra/`. Modulos de negocio devem
    //    permanecer agnosticos a infraestrutura tecnica transversal;
    //    o contrario e permitido (infra pode tocar tipos de dominio
    //    quando expostos em DTO publico).
    //
    //    NOTE: regra estrita por padrao. Quando algum utilitario de
    //    `infra/` precisar ser usado por um modulo, prefira mover o
    //    contrato para a raiz do modulo ou adicionar excecao explicita
    //    via `.orShould()`.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule domain_does_not_depend_on_infra =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infra..");

    // ------------------------------------------------------------------
    // 7) Camadas internas: dentro de um modulo, `web` depende de
    //    `usecase` e `usecase` depende de `persistence`. A direcao
    //    inversa quebra. Usa SlicesRule para detectar ciclos entre as
    //    tres camadas em qualquer modulo de `domain/`.
    // ------------------------------------------------------------------
    @ArchTest
    static final ArchRule layers_inside_a_module_are_acyclic =
            SlicesRuleDefinition.slices()
                    .matching("..domain.(*).internal.(*)..")
                    .should().beFreeOfCycles();
}
