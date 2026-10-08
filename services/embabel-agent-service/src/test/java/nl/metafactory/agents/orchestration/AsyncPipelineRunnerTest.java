package nl.metafactory.agents.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncPipelineRunnerTest {

    @Test
    void runInvokesTheGivenTask() {
        var runner = new AsyncPipelineRunner();
        var executed = new AtomicBoolean(false);

        runner.run(() -> executed.set(true));

        assertThat(executed.get()).isTrue();
    }
}
