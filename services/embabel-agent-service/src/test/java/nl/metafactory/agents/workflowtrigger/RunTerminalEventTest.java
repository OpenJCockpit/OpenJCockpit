package nl.metafactory.agents.workflowtrigger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunTerminalEventTest {

    @Test
    void accessorsReturnPassedValues() {
        var event = new RunTerminalEvent("run-1", "COMPLETED");

        assertThat(event.runId()).isEqualTo("run-1");
        assertThat(event.status()).isEqualTo("COMPLETED");
    }
}
