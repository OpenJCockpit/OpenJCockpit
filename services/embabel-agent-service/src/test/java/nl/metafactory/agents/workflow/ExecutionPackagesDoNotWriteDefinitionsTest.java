package nl.metafactory.agents.workflow;

import com.embabel.common.ai.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-03 (workflow-execution-state-to-database, AC-09): a dependency-free structural guard —
 * deliberately not ArchUnit or any classpath-scanning library (ADR-D13, OD-1) — proving that no
 * bean whose class lives in {@code nl.metafactory.agents.orchestration},
 * {@code nl.metafactory.agents.workflowtrigger} or {@code nl.metafactory.agents.approval} can
 * write a workflow definition: no declared field and no constructor parameter of such a bean has
 * a type assignable to {@link WorkflowDefinitionRepository} or {@link YamlDefinitionStore}.
 * Sound because CLAUDE.md mandates constructor injection repository-wide, so every real
 * dependency of a Spring-managed bean is observable as either a field or a constructor parameter.
 *
 * <p>Three classes are excluded, each with a load-bearing, independently verified reason — none
 * of the three appears in the README's BR-11 list of the six residual definition writers, because
 * none of them writes a workflow definition at all:
 * <ul>
 *   <li>{@code WorkflowExecutionService} (package {@code nl.metafactory.agents.workflow}, so it
 *       would not be scanned by this test anyway) legitimately keeps
 *       {@link WorkflowDefinitionRepository} for {@code loadWorkflow}, a read; it is covered
 *       behaviourally instead by {@code WorkflowDefinitionFileUntouchedByRunIntegrationTest} and
 *       {@code WorkflowExecutionServiceTest} (DoD item 10).</li>
 *   <li>{@code WorkflowOrbRunner} (package {@code workflowtrigger}) holds
 *       {@link WorkflowDefinitionRepository} to READ a child workflow's own definition (agent
 *       ids, orbs) before starting it as an orb child — the same read-only need as
 *       {@code WorkflowExecutionService}, verified by inspecting its source: it only calls
 *       {@code findById}, never {@code save}.</li>
 *   <li>{@code ApprovalDecisionAuditRepository} (package {@code approval}) holds
 *       {@link YamlDefinitionStore} not for workflow definitions at all, but to reuse the same
 *       generic YAML-backed file-store utility for an entirely unrelated policy-decision audit
 *       log, pre-existing and out of this delivery's scope — verified by inspecting its source:
 *       it never touches the {@code workflows} directory or {@link WorkflowDefinitionRepository}.</li>
 * </ul>
 *
 * <p>Named residual gap (DoD item 10): this reflective guard does not catch a static accessor, an
 * {@code ApplicationContext.getBean(...)} lookup, or an indirect call via a third collaborator —
 * accepted, not hidden.
 */
@SpringBootTest
class ExecutionPackagesDoNotWriteDefinitionsTest {

    private static final Set<String> RESTRICTED_PACKAGE_PREFIXES = Set.of(
            "nl.metafactory.agents.orchestration",
            "nl.metafactory.agents.workflowtrigger",
            "nl.metafactory.agents.approval");

    /**
     * Documented, reasoned exclusions — see the class Javadoc above for why each is safe.
     */
    private static final Set<String> EXCLUDED_SIMPLE_CLASS_NAMES = Set.of(
            "WorkflowOrbRunner",
            "ApprovalDecisionAuditRepository");

    @MockitoBean
    private ModelProvider modelProvider;

    @Autowired
    private ApplicationContext context;

    @Test
    void noRestrictedPackageBeanCanReachTheDefinitionWritePath() {
        String[] beanNames = context.getBeanDefinitionNames();
        int inspectedBeanCount = 0;

        for (String beanName : beanNames) {
            Class<?> beanClass;
            try {
                beanClass = context.getType(beanName, false);
            } catch (Exception e) {
                continue;
            }
            if (beanClass == null || !isInRestrictedPackage(beanClass)) {
                continue;
            }
            if (EXCLUDED_SIMPLE_CLASS_NAMES.contains(beanClass.getSimpleName())) {
                continue;
            }

            inspectedBeanCount++;
            assertNoWriteCapableDependency(beanClass);
        }

        // Sanity check on the guard itself: if this drops to zero, the test would pass vacuously.
        assertThat(inspectedBeanCount).isGreaterThan(0);
    }

    private static boolean isInRestrictedPackage(Class<?> beanClass) {
        Package pkg = beanClass.getPackage();
        if (pkg == null) {
            return false;
        }
        String packageName = pkg.getName();
        return RESTRICTED_PACKAGE_PREFIXES.contains(packageName);
    }

    private static void assertNoWriteCapableDependency(Class<?> beanClass) {
        for (Field field : beanClass.getDeclaredFields()) {
            assertThat(isForbiddenType(field.getType()))
                    .as("field %s.%s must not depend on the definition write path", beanClass.getSimpleName(), field.getName())
                    .isFalse();
        }
        for (Constructor<?> constructor : beanClass.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                assertThat(isForbiddenType(parameterType))
                        .as("a constructor parameter of %s must not depend on the definition write path", beanClass.getSimpleName())
                        .isFalse();
            }
        }
    }

    private static boolean isForbiddenType(Class<?> type) {
        return WorkflowDefinitionRepository.class.isAssignableFrom(type)
                || YamlDefinitionStore.class.isAssignableFrom(type);
    }
}
